# root-login-notification

Jahia OSGi module that sends an email notification whenever the root account successfully logs in. Admin UI at `/jahia/administration/rootLoginNotification`.

## Key Facts

- **artifactId**: `root-login-notification` | **version**: `2.0.2-SNAPSHOT`
- **Java package**: `org.jahia.modules.rootloginnotification`
- **jahia-depends**: `default,graphql-dxm-provider,richtext-ckeditor5`
- **OSGi config PID**: `org.jahia.modules.rootloginnotification`

## Architecture

| Class | Role |
|-------|------|
| `RootLoginListener` | `JahiaEventListener<BaseLoginEvent>`; fires on every `BaseLoginEvent`; only sends email if `isRoot()` |
| `RootLoginNotificationConfig` | `ManagedService` + config value holder; provides `recipient`, `sender`, `subject`, `body` |
| `RootLoginNotificationQueryExtension` | GraphQL query |
| `RootLoginNotificationMutationExtension` | GraphQL mutation |

The listener checks `x-forwarded-for` header first; falls back to `getRemoteAddr()` for the IP address.  
Mail is sent via `MailService.sendMessage(sender, recipient, null, null, subject, null, body)` — HTML body, null text body.

### Email Template Variables

| Variable | Scope | Value |
|---|---|---|
| `{server}` | subject | `request.getServerName()` |
| `{site}` | subject | `request.getParameter("site")` or `"systemsite"` |
| `{ip}` | body | Client IP (x-forwarded-for or remote addr) |
| `{time}` | body | Formatted login timestamp |

Default subject: `[{server} - {site}] Connection notification to the root account`

## GraphQL API

| Operation | Name | Notes |
|-----------|------|-------|
| Query | `rootLoginNotificationSettings` → `{recipient, sender, subject, body}` | Returns defaults when config not yet written |
| Mutation | `rootLoginNotificationSaveSettings(recipient, sender, subject, body)` → Boolean | Writes to OSGi config file |

Both require `admin` permission.

## Build

```bash
mvn clean install
yarn build
yarn lint
```

- Admin route target: `administration-server-configuration:20`
- CSS prefix: `rln_`
- CKEditor5 provided by the `richtext-ckeditor5` Module Federation remote (not bundled locally)

## Tests (Cypress Docker)

```bash
cd tests
cp .env.example .env          # fill JAHIA_IMAGE, JAHIA_LICENSE, SMTP config
yarn install
./ci.build.sh && ./ci.startup.sh
```

- Tests: `tests/cypress/e2e/01-rootLoginNotification.cy.ts`
- Includes a **mailpit** container for capturing sent emails
- Tests cover: GraphQL API (read/save settings), admin UI, email delivery (triggers root login, asserts email arrives in mailpit)
- `assets/provisioning.yml` installs dependencies and configures SMTP pointing to mailpit

## Gotchas

- Only fires for `isRoot()` — non-root logins are silently ignored
- The notification is triggered by a successful login event only — failed login attempts do not fire `BaseLoginEvent`
- If `MailService.isEnabled()` returns `false` (SMTP not configured), the email is skipped silently — no error is logged
- `{site}` falls back to `"systemsite"` if the `site` parameter is absent from the login request (common for programmatic logins)
- CSS Modules: match in Cypress with `[class*="rln_..."]`
