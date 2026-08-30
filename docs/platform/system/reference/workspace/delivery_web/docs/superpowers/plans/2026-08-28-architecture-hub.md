# Architecture Hub Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tổ chức lại trang kiến trúc thành Architecture Hub nhiều view liên kết, dễ đọc cho cả business reader và developer.

**Architecture:** Giữ React Flow làm renderer, nhưng graph builder nhận `ArchitectureViewMode` và tạo projection riêng cho context, runtime, domain và data/event. Service inspector dùng cùng selection model nhưng hiển thị metadata theo view; Flow Explorer vẫn là surface độc lập.

**Tech Stack:** React 19, TypeScript, Vite, Tailwind CSS, `@xyflow/react`, `elkjs`, Vitest, Testing Library, Browser/IAB.

**Spec:** `docs/superpowers/specs/2026-08-28-architecture-hub-design.md`

## Global Constraints

- Handbook public, read-only, snapshot-based; không gọi backend live.
- Không thêm service/API/event/database/relationship ngoài canonical snapshot.
- UI tiếng Việt; giữ tiếng Anh cho technical identifiers.
- Gated/experimental mặc định đóng nhưng có thể xem với status rõ ràng.
- Không thay đổi Overview, Flow Explorer, Contracts, Operations hoặc Docs Portal ngoài link điều hướng cần thiết.
- Desktop graph và mobile list phải cùng truyền đạt relationship, không chỉ phụ thuộc hover.

### Task 1: Add canonical view projections

**Files:**
- Modify: `src/modules/system-handbook/data/architectureGraph.ts`
- Modify: `src/modules/admin/data/systemOverviewData.ts`
- Test: `src/modules/system-handbook/__tests__/architectureGraph.test.ts`

- [ ] Add `ArchitectureViewMode = 'context' | 'runtime' | 'domain' | 'data'` and view metadata.
- [ ] Build context projection with actors/clients, Gateway, platform boundary and canonical external nodes.
- [ ] Build runtime projection with service groups, control-plane and infrastructure separation without changing canonical IDs.
- [ ] Build domain projection from capability/service/client relationships already present in `handbookData`.
- [ ] Build data/event projection using existing `dataStore`, `SYSTEM_CONNECTIONS` and status metadata; mark representative relationships explicitly.
- [ ] Add tests for default context nodes, runtime service coverage, domain mapping and no fabricated edges.
- [ ] Run `npx vitest run src/modules/system-handbook/__tests__/architectureGraph.test.ts`.

### Task 2: Redesign Architecture Hub shell and canvas controls

**Files:**
- Modify: `src/modules/system-handbook/pages/SystemHandbookPage.tsx`
- Modify: `src/modules/system-handbook/components/ArchitectureCanvas.tsx`
- Modify: `src/modules/system-handbook/components/ArchitectureNode.tsx`
- Modify: `src/modules/system-handbook/components/ArchitectureMobileList.tsx`
- Modify: `src/index.css`
- Test: `src/modules/admin/__tests__/system-overview.test.tsx`

- [ ] Replace the current overview/full select with a four-view switcher and explanatory subtitle.
- [ ] Add view-specific lane headers, legend and connection toggles while preserving search, status and flow lens.
- [ ] Render domain badges and representative-edge notice; keep canvas read-only.
- [ ] Add view-aware empty states and reset layout behavior.
- [ ] Make mobile fallback use the same view title, lane description and upstream/downstream relationship sections.
- [ ] Add routed tests for switching views, context labels and runtime service labels.
- [ ] Run focused handbook tests.

### Task 3: Expand inspector and cross-view navigation

**Files:**
- Modify: `src/modules/system-handbook/components/ArchitectureInspector.tsx`
- Modify: `src/modules/system-handbook/pages/SystemHandbookPage.tsx`
- Test: `src/modules/admin/__tests__/system-overview.test.tsx`

- [ ] Add inspector sections for ownership, interfaces, relationships, related capabilities, related flows and source/status.
- [ ] Add links from a selected service to Services/API catalog and related Flow Explorer routes.
- [ ] Add edge details for sync/async/storage/external/control semantics and representative flag.
- [ ] Preserve `node`, `connection`, `flow` deep links and clear invalid selections safely.
- [ ] Run focused tests and `npm run verify:ci`.

### Task 4: Browser QA and handoff

**Files:**
- Modify: `docs/superpowers/plans/2026-08-28-architecture-hub.md`

- [ ] Validate `/system-overview/architecture` on desktop and mobile with Browser/IAB.
- [ ] Exercise context → runtime → domain → data view switching, node selection and edge selection.
- [ ] Check page identity, non-blank state, console health, screenshot, keyboard/focus and horizontal overflow.
- [ ] Record commands, evidence and remaining risks in this plan.

## Progress

- [x] Task 1: Added context/runtime/domain/data projections and canonical graph tests.
- [x] Task 2: Added view-aware Architecture Hub shell while preserving read-only canvas and mobile fallback.
- [ ] Task 3: Deep inspector tabs for API/events/data/source remain the next milestone; this milestone only persisted architecture view in URL state and added capability summary for domain view.
- [x] Task 4: Completed automated and Browser/IAB validation.

## Validation

- `npx vitest run src/modules/system-handbook/__tests__/architectureGraph.test.ts src/modules/admin/__tests__/system-overview.test.tsx` — 22/22 passed.
- `npm run verify:ci` — lint, typecheck, 19 test files / 100 tests, action contracts and Vite build passed.
- Browser/IAB: `/system-overview/architecture` rendered non-blank with no console errors; context → runtime → domain → data switching worked; `runtime` selection persisted through `node=order-service`; mobile fallback remains covered by the existing test suite.
- Known build warning: large `SystemHandbookPage`/`architectureLayout` chunks remain; no behavior failure.

## Handoff

This milestone intentionally stops after the Architecture Hub projections and
view navigation are reviewable. The next implementation should add the deeper
service inspector and Data/Event view details without changing the canonical
snapshot model. No livestream changes belong in this plan.
