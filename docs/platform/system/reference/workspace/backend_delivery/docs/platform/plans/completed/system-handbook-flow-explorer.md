# Execution Plan: System Handbook Flow Explorer redesign

Date: 2026-08-22

## Outcome

Redesign `/system-overview` into a connected, Vietnamese-first Flow Explorer.
Readers can choose a workflow, inspect its happy/failure path, see participating
actors/services/API/events, and open an explanation for each step without auth,
live backend calls, or business mutations.

## Decisions

- Flow Explorer is the primary interaction model.
- All main workflows and classified gated/experimental boundaries remain
  discoverable.
- Desktop uses flow selector + swimlane canvas + inspector; mobile uses a
  horizontally scrollable canvas and inspector drawer.
- Happy and failure/recovery paths are toggleable on the same workflow.
- Workflow, step, mode, node and contract selection are URL-addressable.
- API/event detail is progressive: summary first, contract details on expand.
- Architecture highlights the nodes and edges used by the selected flow.
- Existing handbook routes remain compatible.

## Progress

- [x] Normalize flow, interaction, event and contract-link data.
- [x] Implement Flow Explorer and inspector.
- [x] Implement architecture overlay and route state.
- [x] Validate responsive behavior, tests and build.

## Result

Implemented in `delivery_web` with a flow-first workspace. The default COD flow
has ten linked steps, API/event interactions, durable state hints and four
failure/recovery branches. Registration, auth, catalog, restaurant management,
tracking, rating, settlement and gated/experimental boundaries are also
available from the same Flow Explorer.

The selected workflow, step, mode, node, operation and event are URL-addressable.
Architecture and Services & Contracts reuse the same inspector so a reader can
move from a step to its service/API/event and back without losing context.

Validation passed:

- `npm run verify`: 13 test files, 65 tests, lint, typecheck, action contracts,
  handbook drift check and production build.
- Browser smoke check: COD happy path, failure mode, SHIPPER_NOT_FOUND branch,
  step inspector and responsive mobile layout.
- `git diff --check`.

The root documentation verifier still reports the pre-existing stale offline
reference bundle and source-derived HTTP contract. Those generated artifacts
were not refreshed because the backend worktree contains unrelated dirty
changes and regenerating them would expand this task beyond the web redesign.

## Validation

- Handbook route and interaction tests.
- Data-link integrity tests for workflow/API/event/service references.
- `npm run lint`, `npm run typecheck`, `npm test`, `npm run handbook:check`,
  `npm run test:actions`, `npm run build`.
- Desktop and mobile browser smoke checks for COD happy/failure paths.
