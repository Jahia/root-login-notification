import {DocumentNode} from 'graphql';

describe('Root Login Notification', () => {
    const adminPath = '/jahia/administration/rootLoginNotification';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getSettings: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getSettings.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const saveSettings: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/saveSettings.graphql');

    before(() => {
        cy.login();
    });

    // Restore neutral settings after the suite so other tests are not affected
    after(() => {
        cy.apollo({
            mutation: saveSettings,
            variables: {
                recipient: null,
                sender: null,
                subject: '[{server} - {site}] Connection notification to the root account',
                body: '<p>Hi,</p><p>We\'re sending this email following a successful connection to the root user.</p><p>Connection IP: {ip}<br>Connection time: {time}</p><p>Regards,</p>'
            }
        });
    });

    // ─── Settings API ────────────────────────────────────────────────────────────

    describe('Settings API', () => {
        it('returns all settings fields via GraphQL', () => {
            cy.apollo({query: getSettings})
                .its('data.rootLoginNotificationSettings')
                .should(s => {
                    expect(s).to.have.property('recipient');
                    expect(s).to.have.property('sender');
                    expect(s).to.have.property('subject');
                    expect(s).to.have.property('body');
                });
        });

        it('saves settings and returns true', () => {
            cy.apollo({
                mutation: saveSettings,
                variables: {
                    subject: '[{server} - {site}] Test notification',
                    body: '<p>Test body {ip} {time}</p>'
                }
            })
                .its('data.rootLoginNotificationSaveSettings')
                .should('eq', true);
        });

        it('saves settings and reads them back consistently', () => {
            const testSubject = '[test] Root login on {server}';
            cy.apollo({
                mutation: saveSettings,
                variables: {
                    recipient: 'roundtrip@jahia.test',
                    subject: testSubject,
                    body: '<p>IP: {ip}</p>'
                }
            });
            cy.apollo({query: getSettings})
                .its('data.rootLoginNotificationSettings')
                .should(s => {
                    expect(s.recipient).to.eq('roundtrip@jahia.test');
                    expect(s.subject).to.eq(testSubject);
                });
        });

        it('clears optional recipient and sender when saved as null', () => {
            cy.apollo({
                mutation: saveSettings,
                variables: {
                    recipient: null,
                    sender: null,
                    subject: '[{server}] Null-fields test',
                    body: '<p>Body</p>'
                }
            });
            cy.apollo({query: getSettings})
                .its('data.rootLoginNotificationSettings')
                .should(s => {
                    expect(s.recipient).to.be.null;
                    expect(s.sender).to.be.null;
                });
        });

        it('rejects an invalid recipient email address', () => {
            cy.apollo({
                mutation: saveSettings,
                variables: {
                    recipient: 'not-a-valid-email',
                    subject: '[{server}] Test',
                    body: '<p>Body</p>'
                },
                errorPolicy: 'all'
            }).should(result => {
                // The mutation should either return false or throw a GraphQL error
                const saved = result.data?.rootLoginNotificationSaveSettings;
                const hasErrors = result.errors && result.errors.length > 0;
                expect(saved === false || hasErrors).to.be.true;
            });
        });
    });

    // ─── Admin UI ────────────────────────────────────────────────────────────────

    describe('Admin UI', () => {

        it('shows the admin panel title', () => {
            cy.login();
            cy.visit(adminPath);
            cy.contains('Root Login Notification Settings').should('be.visible');
        });

        it('shows recipient and sender input fields', () => {
            cy.login();
            cy.visit(adminPath);
            cy.get('#rln-recipient').should('be.visible');
            cy.get('#rln-sender').should('be.visible');
        });

        it('shows subject input field', () => {
            cy.login();
            cy.visit(adminPath);
            cy.get('#rln-subject').should('be.visible');
        });

        it('shows the CKEditor for the body field', () => {
            cy.login();
            cy.visit(adminPath);
            cy.get('.ck-editor').should('be.visible');
        });

        it('shows the save button', () => {
            cy.login();
            cy.visit(adminPath);
            cy.contains('button', 'Save settings').should('be.visible');
        });

        it('shows success alert after saving', () => {
            cy.login();
            cy.visit(adminPath);
            cy.get('#rln-subject').clear();
            cy.get('#rln-subject').type('[{{}server}] UI test');
            cy.contains('button', 'Save settings').click();
            cy.get('[class*="rln_alert--success"]').should('be.visible');
        });

        it('shows a validation error for an invalid recipient email', () => {
            cy.login();
            cy.visit(adminPath);
            cy.get('#rln-recipient').clear().type('not-an-email');
            cy.get('#rln-recipient').blur();
            cy.get('[class*="rln_errorMsg"]').should('be.visible');
            cy.contains('button', 'Save settings').should('be.disabled');
        });

        it('clears the validation error once the email is corrected', () => {
            cy.login();
            cy.visit(adminPath);
            cy.get('#rln-recipient').clear().type('valid@example.com');
            cy.get('[class*="rln_errorMsg"]').should('not.exist');
            cy.contains('button', 'Save settings').should('not.be.disabled');
            // Clean up
            cy.get('#rln-recipient').clear();
        });
    });

    // ─── Email notification ──────────────────────────────────────────────────────

    describe('Email notification', () => {
        const testRecipient = 'admin@jahia.test';
        const testSubjectTemplate = 'Connection notification to the root account';
        const testBody = '<p>Connection from: {ip} at {time}</p>';

        before(() => {
            cy.login();

            // Point the module at our mailpit-controlled address
            cy.apollo({
                mutation: saveSettings,
                variables: {
                    recipient: testRecipient,
                    subject: testSubjectTemplate,
                    body: testBody
                }
            });

            // Start with an empty inbox
            cy.mailpitDeleteAllEmails();
        });

        it('sends a notification email when root logs in', () => {
            cy.clearCookies();
            // Trigger root login via the same valve that fires LoginEvent
            cy.login()

            // mailpitHasEmailsByTo polls internally until the email arrives (up to 30 s)
            cy.mailpitHasEmailsByTo(testRecipient, 0, 50, {timeout: 30000});

            // mailpitGetMail() defaults to id="latest" and returns the full message
            cy.mailpitGetMail().then(mail => {
                // Correct recipient
                const toAddresses = mail.To.map((t: {Address: string}) => t.Address);
                expect(toAddresses).to.include(testRecipient);

                // Subject tokens were replaced: no raw {server} or {site} left
                expect(mail.Subject).to.not.contain('{server}');
                expect(mail.Subject).to.not.contain('{site}');

                // Static part of the subject is present
                expect(mail.Subject).to.contain('Connection notification to the root account');
            });
        });

        it('email body has IP and time tokens replaced', () => {
            cy.clearCookies();
            // Trigger root login via the same valve that fires LoginEvent
            cy.login();
            cy.wait(10000);
            // mailpitGetMail() returns the full message including HTML and Text body
            cy.mailpitGetMail().then(mail => {
                const body: string = mail.HTML || mail.Text || '';

                expect(body).to.not.contain('{ip}');
                expect(body).to.not.contain('{time}');

                // A real IP address (v4 or v6) should be present
                expect(body).to.match(/\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}|[0-9a-f:]{3,}/i);
            });
        });

        it('does not send a notification email when a non-root user logs in', () => {
            cy.mailpitDeleteAllEmails();

            // Log in as a regular admin user — should NOT trigger a notification
            cy.request({
                method: 'POST',
                url: '/cms/login',
                form: true,
                body: {
                    username: 'mathias',
                    password: 'password',
                    redirect: '/'
                },
                followRedirect: false,
                failOnStatusCode: false
            });

            // Give Jahia a moment to process the login event
            cy.wait(3000);

            cy.mailpitGetAllMails().then(mails => {
                expect(mails.messages_count).to.eq(0);
            });
        });
    });
});
