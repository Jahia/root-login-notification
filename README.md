# root-login-notification

Sends an email notification whenever the `root` account logs in to Jahia. Uses the mail service already configured in Jahia, with fully customisable subject and body templates.

## Requirements

- Jahia 8.2.1.0 or later
- `graphql-dxm-provider` module
- `richtext-ckeditor5` module
- A mail server configured in Jahia (**Administration → Server settings → Configuration → Mail server**)

## Installation

- In Jahia, go to **Administration → Server settings → System components → Modules**
- Upload the JAR `root-login-notification-X.X.X.jar`
- Check that the module is started

## Configuration

Go to **Administration → Server settings → Configuration → Root login notification**.

| Field | Description | Default |
|-------|-------------|---------|
| **Recipient** | Email address that receives the notification. Leave empty to use the Jahia mail service default. | *(Jahia default)* |
| **Sender** | From address used for the notification. Leave empty to use the Jahia mail service default. | *(Jahia default)* |
| **Subject** | Subject template. Supports `{server}` (hostname) and `{site}` (site key) tokens. | `[{server} - {site}] Connection notification to the root account` |
| **Body** | HTML body template. Supports `{ip}` (client IP) and `{time}` (login timestamp) tokens. Editable with a rich-text editor. | *(HTML template)* |

Settings can also be managed via file or GraphQL — see the sections below.

### File-based configuration

Create or edit `org.jahia.community.rootloginnotification.cfg` in the Jahia configuration directory:

```properties
# Optional — leave commented to use the Jahia mail service default
#recipient=security@example.com
#sender=noreply@example.com

subject=[{server} - {site}] Connection notification to the root account
body=<p>Hi,</p><p>We're sending this email following a successful connection to the root user.</p><p>Connection IP: {ip}<br>Connection time: {time}</p><p>Regards,</p>
```

## GraphQL API

All operations are grouped under the `rootLoginNotification` namespace field and require the custom `rootLoginNotificationAdmin` permission (the module ships a `root-login-notification-administrator` role that grants it) — not the generic `admin` permission.

### Query

```graphql
query {
    rootLoginNotification {
        settings {
            recipient   # String (null = Jahia default)
            sender      # String (null = Jahia default)
            subject
            body
        }
    }
}
```

### Mutation

```graphql
mutation {
    rootLoginNotification {
        saveSettings(
            recipient: "security@example.com"  # optional
            sender: "noreply@example.com"      # optional
            subject: "[{server}] Root login"
            body: "<p>IP: {ip} — {time}</p>"
        )
    }
}
```

Returns `true` on success, `false` on error. Passing `null` for `recipient` or `sender` resets them to the Jahia mail service default. Invalid email addresses are rejected.

## Security & trust model

The `{ip}` and `{server}` tokens derive from client-controllable request data:

- **`{ip}`** — read from `X-Forwarded-For` (advisory: any client can forge it, as no trusted-proxy chain is enforced). The real TCP socket peer (`getRemoteAddr()`) is always reported alongside it (e.g. `1.2.3.4 (socket: 10.0.0.9)`), so a spoofed XFF cannot hide the connecting peer.
- **`{server}`** — the client-supplied Host header (advisory). CR/LF header-injection is stripped, but forgery of an otherwise-valid hostname is out of scope for this notification.