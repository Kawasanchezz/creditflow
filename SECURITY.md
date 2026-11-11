# Security Policy

CreditFlow is an academic project (credit simulation and approval pipeline). It has not been through a professional security assessment and must not be used to process real financial data.

## Supported versions

Only the latest commit on `main` is supported.

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's private vulnerability reporting ("Security" tab, "Report a vulnerability") on this repository. Expect an acknowledgement within 7 days; this is a student project, so fixes are made on a best-effort basis.

## Controls in place

- Input validation on every request field (ranges, digits, maximum term of 480 months) and a 400 response that does not expose internal class names.
- All money math uses `BigDecimal`; state transitions are validated in one place; contract creation is idempotent and enforced by database `UNIQUE` constraints.
- Container runs as a non-root user; base images are pinned by digest; database and score mock are not published to the host; the API port is bound to `127.0.0.1`.
- No credentials in the repository: the database password comes from `.env` (ignored by Git, template in `.env.example`).
- GitHub Actions use read-only permissions and actions pinned to commit SHAs; Dependabot covers Maven, Docker and Actions.
- Secret scanning before commit: `pip install pre-commit && pre-commit install` enables the gitleaks hook from `.pre-commit-config.yaml`.

## Known limitations (not fixed on purpose)

- **No authentication or authorization.** Any client that can reach the API can read or contract any proposal by id (IDOR by design of the exercise). Do not expose it to untrusted networks; adding Spring Security with ownership checks is the next step.
- **No rate limiting or request throttling.** Input limits bound the cost of a single request, but not the request rate.
- **Spring Boot 3.3.x is out of open-source support.** It is kept at the latest patch (3.3.13); moving to a supported 3.5.x line is recommended before any real use.
- The Swagger UI is disabled by default and only enabled by `docker-compose.yml` for local demos.
- The CET is an approximation (no fees or IOF); it is not a regulatory figure.

## Recommended repository settings

Enable GitHub secret scanning with push protection, private vulnerability reporting, Dependabot alerts, CodeQL code scanning and branch protection on `main`.
