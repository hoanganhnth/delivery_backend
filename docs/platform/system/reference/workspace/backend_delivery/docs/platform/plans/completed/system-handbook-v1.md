# Execution Plan: Delivery System Handbook v1

Date: 2026-08-22

## Status

Active

## Outcome

Extend `delivery_web` with a standalone, public, read-only system handbook at
`/system-overview`. A reader must be able to understand the platform's actors,
capabilities, workflows, connected architecture, service ownership, API
families, data/security boundaries and active/hidden status without logging in
or using a live backend.

## Context

- Product authority: `docs/product/overview.md` and `delivery_web/APP_FEATURES.md`.
- Architecture authority: `docs/ARCHITECTURE.md` and `docs/system/`.
- Service/API authority: `docs/system/service-catalog.md`,
  `docs/system/api/http-contract.json` and
  `backend_delivery/docs/http-api-inventory.md`.
- Existing technical surface: `delivery_web/src/modules/admin/pages/SystemOverviewPage.tsx`.
- The workspace is a dirty polyrepo; unrelated user changes must be preserved.

## Scope

In scope:

- A compact workspace shell with nested handbook routes under
  `/system-overview`.
- Overview, capability matrix, actor views, workflows with failure branches,
  connected architecture, service detail, classified API catalog and static
  operations/security sections.
- Generated and checked repository snapshot data with stable IDs and source
  references at file/section granularity.
- Search, filtering, cross-link drill-down, keyboard accessibility and
  responsive behavior.
- Documentation/source-map updates and focused/full validation.

Out of scope:

- Live health, logs, metrics or runtime API calls.
- Try-it API execution, mutations, admin actions or portal navigation.
- Exposing secrets, customer data, credentials or internal paths as public
  contracts.
- Changes to backend HTTP/Kafka/WebSocket contracts or business portal flows.

## Approach

1. Add a source-derived manifest generator/check command that reads the
   authoritative system docs, API manifest, service inventory and client
   feature docs, then writes a generated handbook manifest inside
   `delivery_web`.
2. Move the architecture surface into a standalone system-handbook module and
   keep `/system-overview` compatible while adding nested routes for each
   section. Keep the handbook outside auth-dependent portal rendering.
3. Build the compact workspace shell: persistent section rail, central content,
   related-context panel, global search and URL-persisted filters.
4. Implement all handbook sections in the same v1 release, using progressive
   disclosure so the page remains compact instead of becoming a grid of
   unrelated cards.
5. Add source/classification guards, route/interaction tests, responsive visual
   checks and repository validation before handoff.

## Risks And Recovery

- Source docs and generated API inventory may drift: fail the generator check
  and update the owning source before refreshing the snapshot.
- Internal/dev/experimental entries may be mistaken for public capabilities:
  require explicit classification badges and separate public-contract wording.
- Existing dirty changes overlap the architecture files: inspect each diff
  before editing; preserve unrelated hunks and avoid destructive git commands.
- The generated handbook may become too dense: use progressive disclosure and
  context panels, then validate at mobile and desktop widths.

## Progress

- [x] Confirm product scope, access model, data mode, navigation and UX choices.
- [x] Inspect current route, architecture data, documentation authority and
  cross-repository boundaries.
- [x] Implement manifest and generator/check flow.
- [x] Implement standalone nested route shell and handbook sections.
- [x] Add tests, docs checks and desktop browser verification.
- [ ] Record result and move this plan to `docs/plans/completed/`.

## Decisions

- 2026-08-22: Use the existing `delivery_web` project, not a new docs app.
- 2026-08-22: Keep the handbook public read-only and snapshot-based.
- 2026-08-22: Support technical and product readers with progressive detail.
- 2026-08-22: Catalog exhaustive source capabilities, but classify internal,
  dev-only, experimental and gated entries instead of presenting them as
  public contracts.
- 2026-08-22: Show source references as file + section, not unstable source
  line links.

## Validation

- Focused proof: manifest schema/classification tests, nested route tests,
  search/filter/drill-down tests and workflow branch tests.
- Browser proof: public no-login entry, direct nested routes, keyboard/focus,
  mobile layout and desktop relationship canvas without overflow.
- Repository checks: `npm run verify`, handbook generator `--check` and
  `node docs/system/verify-docs.mjs`.

## Result

Implemented in `delivery_web`. The public handbook is available at
`/system-overview` with nested section routes, source snapshot generation and
classification guards. The generated public snapshot contains 172 operations;
Firebase push-token endpoints are intentionally excluded as notification-client
implementation details rather than public platform contracts.

Validation passed:

- `npm run verify` (lint, typecheck, 64 tests, action contracts, handbook drift
  check and production build).
- Desktop browser smoke check on `/system-overview` at localhost:5173: public
  shell rendered with the complete section rail and overview content.

Remaining repository-level limitation: the root cross-repository documentation
verifier still reports its pre-existing stale offline/reference bundles; no
backend reference artifacts were regenerated because that worktree contains
unrelated dirty changes. Responsive behavior is covered by the handbook layout
and focused route tests but was not separately browser-sized in this run.
