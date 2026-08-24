# System Diagram Standards

> Scope: diagrams that explain the Delivery platform at system level. The
> diagrams are documentation views, not a replacement for source, tests,
> migrations, runtime evidence or an approved production decision.

The generated [`reference/`](./reference/README.md) mirror is intentionally
excluded from these style rules: it preserves first-party source documents for
offline lookup and must be refreshed by its sync script, not hand-edited. The
rules below apply to the editable system corpus and the root editable source
[`docs/ARCHITECTURE.md`](../ARCHITECTURE.md).

## What a large-system diagram must make clear

Every diagram should answer one question at one abstraction level. A reader
must be able to tell:

1. whether the view is **as-built**, **target/decision-required** or
   **designed/not implemented**;
2. which trust, network, domain or ownership boundary a node belongs to;
3. whether an interaction is synchronous, asynchronous, external or telemetry;
4. which document, source inventory or executable evidence owns the detail; and
5. what the diagram intentionally omits.

Do not turn a system map into a list of every class, route or database column.
Link those details to the service catalog, generated HTTP contract, event/data
inventory and source map.

## Diagram inventory

| View | File | Mermaid type | Level/purpose | Authority and status |
| --- | --- | --- | --- | --- |
| Polyrepo context and full editable map | [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md) | `flowchart`, `sequenceDiagram`, `stateDiagram-v2` | Context, containers, dynamic flows, state, data topology | Source/config and linked ADRs; as-built |
| Rebuild-oriented container map | [`architecture.md`](./architecture.md) | `flowchart` | Trust zones, clients, domain services and infrastructure | Source map; as-built |
| Client auth/session boundary | [`clients.md`](./clients.md) | `sequenceDiagram` | Client → Gateway → resource/Auth refresh path | Client source and Gateway contract; as-built |
| Registration, COD and tracking workflows | [`workflows.md`](./workflows.md) | `sequenceDiagram`, `stateDiagram-v2` | Dynamic behavior and failure boundaries | Backend workflow/inventory; as-built with stated gaps |
| Service dependency/ownership map | [`service-catalog.md`](./service-catalog.md) | `flowchart` | Contract dependencies and data ownership | Service inventory and Compose; as-built |
| Security trust model | [`security.md`](./security.md) | `flowchart` | Token, internal credential and secret boundaries | JWKS ADR and security runbooks; as-built |
| Production deployment target | [`operations/README.md`](./operations/README.md) | `flowchart` | Network/data/secret placement | Deployment decision packet; target, not deployed proof |
| Observability signal path | [`operations/observability.md`](./operations/observability.md) | `flowchart` | Correlation, tracing, metrics and alert flow | Runtime config; local instrumentation plus open production decisions |
| Simulator control/observation boundary | [`simulator/README.md`](./simulator/README.md) | `flowchart` | Isolated test control/observation boundary | MVP runner/console partial; observer/storage open |

## Mermaid syntax and portability rules

- Use a fenced block named `mermaid` with triple backticks: ` ```mermaid `.
  Keep one diagram per block. Do not mix tilde fences in the canonical system
  corpus.
- Use `flowchart` rather than the legacy `graph` keyword. Use
  `sequenceDiagram` for time-ordered interactions and `stateDiagram-v2` for
  state transitions. Use quoted labels whenever a label contains punctuation,
  slashes, parentheses or a long phrase.
- Use stable technical node IDs (`Gateway`, `OrderDb`, `Kafka`) and put the
  reader-facing name in the label. Never use an order ID, user ID, event ID or
  environment-specific hostname as a node ID.
- Use `<br/>` for a label line break. Do not use the escaped `\n` sequence; it
  renders differently across GitHub, IDE previews and Mermaid CLI versions.
- Use subgraphs for zones or conceptual groups. Do not use a subgraph ID as an
  edge endpoint; connect representative concrete nodes instead and explain that
  the edge is representative in prose. This keeps layout portable across
  Mermaid renderers.
- Keep edge direction truthful. A bidirectional arrow means a real duplex
  channel, not merely “both components are related.” Avoid crossing edges by
  splitting a dense view into a context map and focused workflow diagrams.

## Edge semantics

| Notation | Meaning in this corpus | Example |
| --- | --- | --- |
| `-->` | Synchronous HTTP, WebSocket handshake/frame path or direct store access | Client → Gateway; Order → Restaurant validation |
| `-.->` | Kafka/event, Redis Pub/Sub/projection, telemetry or other asynchronous dependency | Order → Kafka; Tracking → Match projection |
| `-->>` / `->>` | Sequence-diagram response/request with the normal Mermaid sequence semantics | Gateway → resource service |
| `--x` / `-..->` | Failure or best-effort behavior only when the prose explains the exact meaning | A rejected request or wake-up path |

The public-edge invariant is non-negotiable: application clients go to Gateway;
they never connect directly to a service, database, Kafka, Config Server, Eureka
or JWKS endpoint. Resource services fetch public JWKS from Auth; clients do not
fetch JWKS and do not authorize themselves. Internal service calls must be shown
as private and authenticated, not as a browser/mobile shortcut.

## Status and authority conventions

- Put `Status:` and `checked YYYY-MM-DD` in the document header. Use
  `decision required` for an unselected production policy and
  `designed/not implemented` for a future tool or capability.
- Keep target diagrams under an explicitly named “target” section. A target
  diagram may describe required HA/PITR/WAF/secret-manager properties, but it
  must not be written as current deployment evidence.
- Put the authority and the omitted scope next to a dense diagram. The source
  map is the navigation point when prose and code disagree.
- A diagram may summarize several services, but the service catalog remains the
  authority for ports, ownership, capability status and public/private routes.
  The generated HTTP contract and backend event inventory remain the authorities
  for exact signatures and payload fields.

## Review checklist

Before merging a system-level diagram change:

1. Confirm the source/config/test/runbook authority and update the source-map row
   when the view's claim or freshness changes.
2. Check that no client-to-service or client-to-JWKS edge bypasses Gateway.
3. Check that sync and async edges use the correct notation and that database
   ownership is not represented as a cross-service read.
4. Render the changed block in the project's supported Markdown preview or
   Mermaid CLI when available; then run `node docs/system/verify-docs.mjs`.
5. If an event, API, role, state or storage contract crosses repositories,
   update the owning contract and the system plan/decision as required by
   [`docs/WORKFLOW.md`](../WORKFLOW.md).

The verifier catches fence errors, unsupported diagram headers and the
non-portable `\n` label pattern. It is a structural guard, not a visual proof;
layout, readability and semantic accuracy still require human review.
