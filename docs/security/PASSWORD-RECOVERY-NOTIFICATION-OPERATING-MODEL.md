# SNAD Password Recovery Notification Operating Model

## Security contract

- The system never sends a plaintext password by email.
- Forgotten-password requests always return a generic response to prevent account enumeration.
- Reset tokens are cryptographically random, stored only as SHA-256 hashes, expire after 30 minutes, and are single-use.
- Administrative recovery sends the same one-time set-password link to the targeted active user.
- Successful reset and authenticated credential change generate a separate security confirmation.
- Existing refresh sessions are revoked by the established authentication service.
- Delivery failure revokes the generated self-service token.

## Runtime delivery

`SecurityNotificationGateway` separates SNAD from a specific email vendor. The `http` provider sends a structured message to a separately managed HTTPS notification service. Configure the deployed backend with:

- `SECURITY_NOTIFICATION_PROVIDER=http`
- `SECURITY_NOTIFICATION_ENDPOINT=<HTTPS delivery endpoint>`
- `SECURITY_NOTIFICATION_BEARER_TOKEN=<secret managed by the deployment platform>`
- `SECURITY_NOTIFICATION_FROM=<verified sender>`
- `APPLICATION_BASE_URL=<public SNAD URL>`

No provider secret is committed to this repository. Local and test profiles do not contact an external provider. Outside local/test, a disabled provider fails closed.
