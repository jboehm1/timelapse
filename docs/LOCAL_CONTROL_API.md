# Local control API — proposed contract, not implemented

**Status: no local HTTP/WebSocket control service exists in the current app.** This document reserves an opt-in design for later review. It does not authorize a server, expose an endpoint, or change the offline-only capture path.

## Security and lifecycle requirements

- Disabled by default; explicit user start from a visible, unlocked app screen. Stop on user action, logout, app stop, or configured idle timeout.
- Bind only to a selected local interface; never listen on all interfaces or forward through the cloud. No capture, schedules or export depend on network availability.
- Pair by displaying a short-lived one-time code on the phone. Exchange for a random high-entropy token, store only a hash where feasible, support revocation/rotation, rate-limit attempts, and require TLS or a documented device-local secure channel before remote control is enabled.
- Require authentication and authorization on every operation. Never expose filesystem paths, raw secrets, full EXIF or site images through status responses.
- Return explicit status and error codes. Remote requests are commands, not success acknowledgements; the response must expose the actual state transition and saved-frame count.

## Proposed operations

| Operation | Request | Response / failure behavior |
|---|---|---|
| Pair | One-time code, client name | Token once; reject expired/reused code and rate-limit retries |
| Get status | Authenticated request | State, project ID alias, saved count, last frame time, thermal/storage warning; no image payload |
| Start / Stop | Project ID, bounded preset ID | Accepted/rejected plus observed state; Start must not claim RECORDING before a JPEG commit |
| Get latest preview | Authenticated request, optional max dimensions | Explicit opt-in preview with short cache; never send original-resolution frames by default |
| Revoke client | Authenticated client ID | Revoke token immediately and log local event |

## Not yet specified for implementation

Transport library, certificate provisioning, pairing UX, replay defense, LAN threat model, API version negotiation, rate limits, and Android permission implications require an ADR and security review. The current permission set and build should be audited before implementation. See roadmap H-06 through H-10 and I-05.
