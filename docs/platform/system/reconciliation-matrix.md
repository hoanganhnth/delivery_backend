# Documentation reconciliation matrix

> Working ledger for the 2026-08-24 as-built documentation refresh. Source code,
> tests, migrations, manifests and observed runtime evidence remain authoritative.

| Surface | Authority | Current proof | Handbook treatment | Owner |
| --- | --- | --- | --- | --- |
| HTTP routes, bindings and DTO reachability | Controllers/tests plus `docs/http-api-inventory.md` and generated `system/api/http-contract.json` | Generator check; route/security tests | Public catalog entries are classified; internal/dev routes are metadata only | Backend |
| Service ownership, ports and capability flags | `docs/system-contract-inventory.md`, Compose/Kubernetes and service docs | Inventory + compose/config checks | Service catalog and status badges | Backend/platform |
| Kafka events, retry/DLT and state transitions | Backend event inventory, producer/consumer tests and runbooks | Focused replay/idempotency tests where recorded | Workflow/event pages; unresolved runtime proof stays open | Backend |
| Database ownership and migrations | Service Flyway migrations/entities/tests | Migration/schema tests | Events & data pages link to owner service | Backend |
| Authentication and authorization | JWKS ADR, gateway/resource-server tests and security runbooks | Gateway security/resource-server tests | Security page; never expose keys or credentials | Backend/platform |
| Customer app behavior | `delivery_app` source/tests/README | Flutter analyze/test when environment permits | Client document with source links and limitations | Customer app |
| Web portal behavior | `delivery_web` action matrix/source/tests/CI | `npm run verify` and E2E coverage gates | Web docs + handbook explorer | Web |
| Shipper behavior and identity | `shipper_app2` source/tests plus Delivery/Shipper contracts | Typecheck/lint/architecture/Jest; runtime proof separately labeled | Shipper docs and batch/identity workflow | Shipper/backend |
| Operations and recovery | Backend runbooks, scripts and manifests | Each command labeled local/staging/production-like | Public summary only; sensitive operator details remain internal | Platform/Ops |
| Simulator | `simulator-service`, `delivery_simulator_web`, simulator docs | Static/code proof; console has no Git repository | `dev-only`; no public production capability claim | Platform/test |

## Freshness rules

- A source-derived artifact is stale when its generator `--check` fails.
- A document may state `active` only when its route/configuration and relevant
  proof agree; code/schema presence alone is insufficient.
- Product and security policy changes require an ADR or accepted decision before
  the handbook can present a new public behavior.
- Historical plans and learning notes remain useful provenance, but they are not
  canonical product truth and are excluded from the public document index.
- Every refresh records the date, generator/check commands and unresolved proof
  gaps in the active execution plan.
