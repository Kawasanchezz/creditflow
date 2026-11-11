# Security Policy

CreditFlow is an academic project (credit simulation and approval pipeline). It has not been through a professional security assessment and must not be used to process real financial data.

## Supported versions

Only the latest commit on `main` is supported.

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's private vulnerability reporting ("Security" tab, "Report a vulnerability") on this repository. Expect an acknowledgement within 7 days; this is a student project, so fixes are made on a best-effort basis.

## Controls in place

- Authentication (HTTP Basic, BCrypt) and server-side ownership checks on every proposal and contract endpoint; only analysts can decide manual reviews.
- Input validation on every request field (ranges, digits, maximum term of 480 months) and a 400 response that does not expose internal class names.
- All money math uses `BigDecimal`; state transitions are validated in one place; contract creation is idempotent and enforced by database `UNIQUE` constraints.
- Container runs as a non-root user; base images are pinned by digest; database and score mock are not published to the host; the API port is bound to `127.0.0.1`.
- No credentials in the repository: the database password comes from `.env` (ignored by Git, template in `.env.example`).
- GitHub Actions use read-only permissions and actions pinned to commit SHAs; Dependabot covers Maven, Docker and Actions.
- Secret scanning before commit: `pip install pre-commit && pre-commit install` enables the gitleaks hook from `.pre-commit-config.yaml`.

## Known limitations (not fixed on purpose)

- **Basic accounts only.** HTTP Basic with in-memory accounts (`analyst`, `customer-N`) whose passwords come from environment variables; customers can only access their own proposals (404 otherwise). There is no account management, lockout, password rotation or TLS in this repository: run it behind TLS and replace it with an OIDC identity provider before any real use.
- **No rate limiting or request throttling.** Input limits bound the cost of a single request, but not the request rate.
- Spring Boot is on the 3.5.x line; keep Dependabot updates enabled.
- The Swagger UI is disabled by default and only enabled by `docker-compose.yml` for local demos.
- The CET is an approximation (no fees or IOF); it is not a regulatory figure.

## Recommended repository settings

Enable GitHub secret scanning with push protection, private vulnerability reporting, Dependabot alerts, CodeQL code scanning and branch protection on `main`.
