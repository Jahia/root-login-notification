# root-login-notification

Jahia OSGi module that sends an email notification whenever the root account successfully logs in. Admin UI at `/jahia/administration/rootLoginNotification`.

## Key Facts

- **artifactId**: `root-login-notification` | **version**: `2.0.5-SNAPSHOT`
- **Java package**: `org.jahia.community.rootloginnotification`
- **jahia-depends**: `default,graphql-dxm-provider,richtext-ckeditor5`
- **OSGi config PID**: `org.jahia.community.rootloginnotification`

## Architecture

| Class | Role |
|-------|------|
| `RootLoginListener` | `JahiaEventListener<BaseLoginEvent>`; fires on every `BaseLoginEvent`; only sends email if `isRoot()` |
| `RootLoginNotificationConfig` | `ManagedService` + config value holder; provides `recipient`, `sender`, `subject`, `body` |
| `RootLoginNotificationQueryExtension` | GraphQL query |
| `RootLoginNotificationMutationExtension` | GraphQL mutation |

The listener reads the `x-forwarded-for` header (first entry of a comma-separated chain) **and** the real TCP socket peer `getRemoteAddr()`. Both are surfaced in the `{ip}` value: `x-forwarded-for` is advisory (client-controllable, no trusted-proxy chain enforced), so the socket peer is always shown alongside it as `<xff> (socket: <remoteAddr>)`. When XFF is absent, or equals the socket peer, only the socket peer is shown. See **Security & trust model** below.  
Mail is sent via `MailService.sendMessage(sender, recipient, null, null, subject, null, body)` — HTML body, null text body.

### Security & trust model

- **`{ip}` (X-Forwarded-For)** — client-controllable and therefore **advisory**. This module does not enforce a trusted-proxy allowlist, so a client can forge XFF. To ensure a spoofed XFF can never *hide* the connecting peer, the real socket address (`getRemoteAddr()`) is always reported alongside the forwarded value. Operators fronting Jahia with a trusted proxy should rely on the socket-peer value (or configure their proxy to overwrite, not append, XFF).
- **`{server}` (Host header)** — also client-controllable. There is no server-authoritative alternative to surface without a configured expected-host allowlist, so `{server}` is **advisory** by accepted design. CR/LF header-injection is still stripped (`sanitizeHost`); only forgery — not injection — is out of scope.

### Input Sanitization (security)

User-controlled values inserted into subject/body are sanitized to prevent SMTP header injection (CRLF) and XSS:

- `{server}`, `{ip}` — `sanitizeHost`: trimmed to 255 chars, stripped to `[A-Za-z0-9._:\-]`
- `{site}` — `sanitizeHeader`: trimmed to 255 chars, control chars (`< 0x20`, `0x7F`) removed
- `{ip}` and `{time}` — additionally HTML-escaped (`StringEscapeUtils.escapeHtml`) before substitution into the HTML body

### Email Template Variables

| Variable | Scope | Value |
|---|---|---|
| `{server}` | subject | `request.getServerName()` |
| `{site}` | subject | `request.getParameter("site")` or `"systemsite"` |
| `{ip}` | body | Forwarded client IP (advisory) plus the real socket peer — e.g. `1.2.3.4 (socket: 10.0.0.9)` |
| `{time}` | body | Formatted login timestamp |

Default subject: `[{server} - {site}] Connection notification to the root account`

## GraphQL API

Operations are grouped under a single namespaced container field `rootLoginNotification` on both `Query` and `Mutation` (not flat root fields):

| Operation | Path | Notes |
|-----------|------|-------|
| Query | `rootLoginNotification { settings { recipient, sender, subject, body } }` | Returns defaults when config not yet written |
| Mutation | `rootLoginNotification { saveSettings(recipient, sender, subject, body) }` → Boolean | Writes to OSGi config `org.jahia.community.rootloginnotification` |

Both require the custom `rootLoginNotificationAdmin` permission (resolved on the JCR root node; the module ships a `root-login-notification-administrator` role granting it) — **not** the generic `admin` permission.

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
