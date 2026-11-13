# Security Policy

CreditFlow is an academic project (credit simulation and approval pipeline). It has not been through a professional security assessment and must not be used to process real financial data.

## Supported versions

Only the latest commit on `main` is supported.

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's private vulnerability reporting ("Security" tab, "Report a vulnerability") on this repository. Expect an acknowledgement within 7 days; this is a student project, so fixes are made on a best-effort basis.

## Controls in place

- CSRF protection stays enabled for any request that can carry browser-ambient credentials (cookies); it is skipped only when the credential travels in the Authorization header or the request has no cookies. The Basic challenge (WWW-Authenticate) is suppressed so browsers never cache and replay credentials. The web UI keeps credentials in memory only and is served with a strict Content-Security-Policy.
- Authentication (HTTP Basic, BCrypt) and server-side ownership checks on every proposal and contract endpoint; only analysts can decide manual reviews.
- Input validation on every request field (ranges, digits, maximum term of 480 months) and a 400 response that does not expose internal class names.
- All money math uses `BigDecimal`; state transitions are validated in one place; contract creation is idempotent and enforced by database `UNIQUE` constraints.
- Container runs as a non-root user; base images are pinned by digest; database and score mock are not published to the host; the API port is bound to `127.0.0.1`.
- No credentials in the repository: the database password comes from `.env` (ignored by Git, template in `.env.example`).
- GitHub Actions use read-only permissions and actions pinned to commit SHAs; Dependabot covers Maven, Docker and Actions.
- Secret scanning before commit: `pre-commit install` (after installing pre-commit) enables the gitleaks hook from `.pre-commit-config.yaml`.

## Known limitations (not fixed on purpose)

- **Basic accounts only.** HTTP Basic with in-memory accounts (`analyst`, `customer-N`) whose passwords come from environment variables; customers can only access their own proposals (404 otherwise). There is no account management, password rotation or TLS in this repository: run it behind TLS and replace it with an OIDC identity provider before any real use.
- **Basic abuse limits only.** Request bodies are capped at 64 KiB and repeated authentication failures from one address are throttled (20 per 5 minutes, then 429). The throttle is per instance and keyed on the socket address, so behind a proxy it needs the proxy's client IP handling; there are no per-user quotas, so request volume is not otherwise bounded.
- **Self-declared policy inputs.** Income, commitments and relationship months in a proposal are supplied by the caller and drive the automatic decision; this is a simulation limitation, not a verified-data pipeline.
- **Shared demo/compose passwords.** All `customer-N` accounts share one password from the environment, and `Idempotency-Key` is a global namespace (ownership is checked before a stored contract is returned).
- **Static demo** (`vercel.json`, `assets/demo.js`): when no backend answers, the browser simulates the API locally (data in `localStorage`, public demo credentials, no server-side security). It is a showcase, not the real service.
- **Demo mode** (`docker-compose.demo.yml`, off by default): `GET /api/demo` publishes public sample credentials so the login screen can offer one-click sign-in, and sample data is seeded. It exists only when `creditflow.demo.enabled=true`, logs a warning at startup, binds to 127.0.0.1 and must never be enabled outside a local evaluation machine.
- Outcome records (contestation, chargeback, default, settlement) are append-only and can only be created by analysts; customers can read the outcomes of their own proposals only. The quality report is analyst-only.
- Dependencies and the container image were scanned with Trivy (all severities) and reported no known vulnerabilities at the time of the last scan; new CVEs appear over time, so keep Dependabot enabled and rescan before releases.
- The Swagger UI is disabled by default and only enabled by `docker-compose.yml` for local demos.
- The CET is an approximation (no fees or IOF); it is not a regulatory figure.

## Recommended repository settings

Enable GitHub secret scanning with push protection, private vulnerability reporting, Dependabot alerts, CodeQL code scanning and branch protection on `main`.
