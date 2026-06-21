import {DocumentNode} from 'graphql';
import {createUser, deleteUser, grantRoles} from '@jahia/cypress';

/**
 * Regression tests for the fine-grained `rootLoginNotificationAdmin` permission.
 *
 * These guard against the gate being silently removed or mismatched across the stack:
 *  - Backend: `@GraphQLRequiresPermission("rootLoginNotificationAdmin")` is enforced as
 *    `session.getNode("/").hasPermission("rootLoginNotificationAdmin")` (root-node ACL check).
 *  - Frontend: `requiredPermission: 'rootLoginNotificationAdmin'` in register.jsx gates the admin route.
 *  - RBAC content: the module ships the assignable `root-login-notification-administrator` role
 *    (src/main/import/roles.xml) granting `administrationAccess` + that permission only.
 *
 * The "allowed" user is granted that role and nothing else — never `admin` — so the tests prove
 * fine-grained granularity, not merely that a full administrator can pass.
 */
describe('Root Login Notification — permission enforcement', () => {
    const ROLE_NAME = 'root-login-notification-administrator';
    const DENIED_USER = 'rlnDeniedUser';
    const ALLOWED_USER = 'rlnAllowedUser';
    const PASSWORD = 'RlnPerm9PwdTest';
    const ADMIN_PATH = '/jahia/administration/rootLoginNotification';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getSettings: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getSettings.graphql');

    const errorsOf = (result: {graphQLErrors?: Array<{message: string}>; errors?: Array<{message: string}>}) =>
        result.graphQLErrors ?? result.errors ?? [];

    const querySettingsAs = (username: string) => {
        cy.apolloClient({username, password: PASSWORD});
        return cy.apollo({query: getSettings});
    };

    before(() => {
        cy.login();
        createUser(DENIED_USER, PASSWORD);
        createUser(ALLOWED_USER, PASSWORD);
        // The annotation resolves the permission on the JCR root node, so grant the
        // module-shipped role on `/`.
        grantRoles('/', [ROLE_NAME], ALLOWED_USER, 'USER');
    });

    after(() => {
        cy.apolloClient(); // reset the current Apollo client back to root
        cy.login();
        deleteUser(DENIED_USER);
        deleteUser(ALLOWED_USER);
    });

    describe('GraphQL API authorization', () => {
        it('denies the gated query for a user without the permission', () => {
            querySettingsAs(DENIED_USER).then((result: never) => {
                const errs = errorsOf(result);
                expect(errs, 'denial errors').to.have.length.greaterThan(0);
                expect(errs.map((e: {message: string}) => e.message).join(' ')).to.contain('Permission denied');
            });
        });

        it('allows the gated query for a user granted only the module permission', () => {
            querySettingsAs(ALLOWED_USER).then((result: never) => {
                expect(errorsOf(result), 'should have no errors').to.have.length(0);
                const settings = (result as {data: {rootLoginNotification: {settings: Record<string, unknown>}}})
                    .data.rootLoginNotification.settings;
                expect(settings).to.have.property('recipient');
                expect(settings).to.have.property('sender');
                expect(settings).to.have.property('subject');
                expect(settings).to.have.property('body');
            });
        });
    });

    describe('Admin UI authorization', () => {
        it('hides the admin panel from a user without the permission', () => {
            cy.login(DENIED_USER, PASSWORD);
            cy.visit(ADMIN_PATH, {failOnStatusCode: false});
            cy.contains('Root Login Notification Settings').should('not.exist');
        });

        it('shows the admin panel to a user granted only the module permission', () => {
            cy.login(ALLOWED_USER, PASSWORD);
            cy.visit(ADMIN_PATH);
            cy.contains('Root Login Notification Settings').should('be.visible');
        });
    });
});
