# Architecture Handbook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a readable, interactive `/system-overview/architecture` page that explains the snapshot architecture through a layered graph, selected node/edge detail, flow highlighting, and a mobile list fallback.

**Architecture:** Keep `SystemHandbookPage` as the route-level coordinator, but move graph transformation, layout, canvas presentation, inspector content, and mobile rendering into focused modules. `SYSTEM_NODES`, `SYSTEM_CONNECTIONS`, and `flowExplorerData` remain the only topology and flow sources; `@xyflow/react` renders the graph and `elkjs` calculates layered positions.

**Tech Stack:** React 19, Vite, TypeScript, Tailwind CSS, `@xyflow/react@12.11.5`, `elkjs@0.12.0`, Vitest, Testing Library, Browser/IAB.

**Spec:** `docs/superpowers/specs/2026-08-27-architecture-handbook-design.md`

## Global Constraints

- The handbook is public, read-only, snapshot-based, and must not call the backend live.
- Do not add service, API, event, database, or connection data outside the canonical sources.
- UI is Vietnamese except service/client names, API paths, event/topic/queue/database names, and technical identifiers.
- Desktop uses an interactive graph; mobile uses layer accordion/list fallback and must not overflow horizontally.
- Existing Overview, Flow Explorer, Services & Contracts, Operations, and Docs Portal behavior must remain unchanged.
- Selected node/edge state must be keyboard reachable and must not rely on color alone.

---

### Task 1: Add graph model and ELK layout helpers

**Files:**
- Create: `src/modules/system-handbook/data/architectureGraph.ts`
- Create: `src/modules/system-handbook/data/architectureLayout.ts`
- Create: `src/modules/system-handbook/__tests__/architectureGraph.test.ts`
- Modify: `package.json`
- Modify: `package-lock.json`

**Interfaces:**
- `architectureGraph.ts` produces `ArchitectureLayer`, `ArchitectureNodeData`, `ArchitectureEdgeData`, `buildArchitectureGraph(options)`, and `getArchitectureSelection(graph, selection)`.
- `architectureLayout.ts` produces `layoutArchitectureGraph(nodes, edges): Promise<ArchitectureLayoutResult>` and a deterministic fallback position map.
- The UI consumes React Flow-compatible node/edge data but the helpers remain testable without rendering a browser.

- [ ] **Step 1: Add the diagram dependencies**

Run:

```bash
npm install @xyflow/react@12.11.5 elkjs@0.12.0
```

Expected: `package.json` and `package-lock.json` contain the two runtime dependencies; no unrelated dependency is added.

- [ ] **Step 2: Write failing graph model tests**

Add tests that assert the canonical node count is preserved, layer filters only return the requested kinds, flow highlighting uses the selected flow's service IDs plus its client/gateway participants, and no connection is fabricated from a `dataStore` string.

```ts
it('builds a full layered graph from canonical nodes and connections', () => {
  const graph = buildArchitectureGraph({ layer: 'all', status: 'all', flowId: null, query: '' });
  expect(graph.nodes).toHaveLength(SYSTEM_NODES.length);
  expect(graph.edges).toHaveLength(SYSTEM_CONNECTIONS.length);
  expect(graph.layers.map((layer) => layer.id)).toEqual(['client', 'edge', 'service', 'infrastructure']);
});

it('does not infer database edges from dataStore text', () => {
  const graph = buildArchitectureGraph({ layer: 'all', status: 'all', flowId: null, query: '' });
  expect(graph.edges.some((edge) => edge.source === 'order-service' && edge.target === 'order_db')).toBe(false);
});

it('marks only canonical flow participants as highlighted', () => {
  const graph = buildArchitectureGraph({ layer: 'all', status: 'all', flowId: 'cod-order', query: '' });
  expect(graph.nodes.find((node) => node.id === 'order-service')?.data.highlighted).toBe(true);
  expect(graph.nodes.find((node) => node.id === 'livestream-service')?.data.highlighted).toBe(false);
});
```

Run:

```bash
npx vitest run src/modules/system-handbook/__tests__/architectureGraph.test.ts
```

Expected: FAIL because the graph helpers do not exist yet.

- [ ] **Step 3: Implement the canonical graph transformation**

Create typed layer/filter options and map every `SYSTEM_NODE` to a node with its canonical label, short label, kind, group, status, icon, description, responsibilities, interface, and optional `dataStore`. Map every `SYSTEM_CONNECTION` to one edge with source, target, label, kind, and detail. Set `highlighted` from selected flow participants and keep non-highlighted nodes/edges present with a `dimmed` flag.

Use the existing `getFlow(flowId)` and derive flow participants from `flow.steps.flatMap(step => step.serviceIds)` plus `gateway`, `customer-app`, `delivery-web`, and `shipper-app`; do not add any other participant IDs.

- [ ] **Step 4: Implement the ELK layered layout with fallback**

Create an `ELK` instance and send a graph with node dimensions `244 × 132`, `elk.algorithm: 'layered'`, `elk.direction: 'RIGHT'`, fixed layer spacing, and edge routing. Convert returned child coordinates to React Flow positions. If the layout rejects, return a stable hand-authored fallback position map grouped by canonical `mapColumn`; never throw into the page.

- [ ] **Step 5: Run the graph tests**

Run:

```bash
npx vitest run src/modules/system-handbook/__tests__/architectureGraph.test.ts
```

Expected: PASS with all graph and fallback assertions.

- [ ] **Step 6: Commit the data/layout slice**

```bash
git add package.json package-lock.json src/modules/system-handbook/data/architectureGraph.ts src/modules/system-handbook/data/architectureLayout.ts src/modules/system-handbook/__tests__/architectureGraph.test.ts
git commit -m "feat(web): add architecture graph model and layout"
```

### Task 2: Build the interactive desktop canvas and inspector

**Files:**
- Create: `src/modules/system-handbook/components/ArchitectureCanvas.tsx`
- Create: `src/modules/system-handbook/components/ArchitectureNode.tsx`
- Create: `src/modules/system-handbook/components/ArchitectureEdge.tsx`
- Create: `src/modules/system-handbook/components/ArchitectureInspector.tsx`
- Modify: `src/modules/system-handbook/pages/SystemHandbookPage.tsx`
- Modify: `src/modules/admin/__tests__/system-overview.test.tsx`
- Modify: `src/index.css`

**Interfaces:**
- `ArchitectureCanvas` accepts prepared React Flow `nodes`, `edges`, `onNodeSelect`, `onEdgeSelect`, and `onFitView`; it renders `ReactFlow`, `Background`, `Controls`, `MiniMap`, and the fixed legend.
- `ArchitectureNode` renders one accessible, non-editable node; `ArchitectureEdge` renders one selectable communication edge.
- `ArchitectureInspector` accepts the selected node/edge plus canonical graph data and renders the corresponding detail sections.
- `SystemHandbookPage` owns URL selection and passes `connection=<id>` for edge deep links without changing other routes.

- [ ] **Step 1: Extend the focused test with failing Architecture behavior**

Add assertions for heading/legend/canonical labels, node click updating `?node=order-service` and showing its responsibilities, edge click updating `?connection=customer-gateway-rest` and showing source/target, and the flow lens control.

```tsx
it('explores architecture nodes and communication edges', async () => {
  const user = userEvent.setup();
  renderApp({ route: '/system-overview/architecture' });

  expect(await screen.findByRole('heading', { name: 'Kiến trúc hệ thống' })).toBeInTheDocument();
  expect(screen.getByText('HTTPS / REST')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: /order-service.*Checkout/ }));
  expect(await screen.findByRole('heading', { name: 'order-service' })).toBeInTheDocument();
  expect(window.location.search).toContain('node=order-service');
  await user.click(screen.getByRole('button', { name: /customer-app.*gateway.*HTTPS REST/ }));
  expect(screen.getByText('Source')).toBeInTheDocument();
  expect(window.location.search).toContain('connection=customer-gateway-rest');
});
```

Run:

```bash
npx vitest run src/modules/admin/__tests__/system-overview.test.tsx
```

Expected: FAIL because the new heading, canvas controls, edge selection, and connection query do not exist.

- [ ] **Step 2: Implement custom node and edge components**

Render technical names at readable size, Vietnamese role/short label, visible status text, and data owner where relevant. Use handles only as non-editable visual attachment points. Style edge types according to `sync`, `async`, `storage`, `external`, and `control`; show labels for highlighted/selected edges and hide only low-priority labels when the graph is dense.

- [ ] **Step 3: Implement the React Flow canvas**

Import `@xyflow/react/dist/style.css` from the application stylesheet. Render the prepared graph with custom `nodeTypes` and `edgeTypes`, `fitView`, a subtle grid background, controls, minimap, keyboard selection, and a toolbar legend. Disable connection creation/deletion and keep the graph read-only. Apply dimming/highlighting from graph data without deleting canonical nodes.

- [ ] **Step 4: Implement node/edge inspector content**

For a node show technical label, short role, description, responsibilities, interface, data owner, status, related flows, and inbound/outbound connections. For an edge show `Source`, `Target`, `Kiểu giao tiếp`, `Label`, and `Chi tiết`. Use `getHandbookService` only for service detail where it exists; fall back to `SystemNode` data for infrastructure/client nodes.

- [ ] **Step 5: Integrate ArchitectureView and URL state**

Replace the four-column Architecture body with a page header, toolbar, canvas/inspector split, and flow-to-architecture summary. Add `connection` to query parsing and `InspectorSelection`, update `buildSelection`, and preserve existing `step`, `node`, `operation`, `event`, and `branch` behavior for other sections. Keep `ArchitectureView` isolated from business mutations and live data.

- [ ] **Step 6: Run focused tests**

Run:

```bash
npx vitest run src/modules/admin/__tests__/system-overview.test.tsx
```

Expected: PASS, including all existing handbook tests and the new Architecture interaction assertions.

- [ ] **Step 7: Commit the desktop canvas slice**

```bash
git add src/modules/system-handbook/components/ArchitectureCanvas.tsx src/modules/system-handbook/components/ArchitectureNode.tsx src/modules/system-handbook/components/ArchitectureEdge.tsx src/modules/system-handbook/components/ArchitectureInspector.tsx src/modules/system-handbook/pages/SystemHandbookPage.tsx src/modules/admin/__tests__/system-overview.test.tsx src/index.css
git commit -m "feat(web): add interactive architecture handbook canvas"
```

### Task 3: Add filtering, flow lens, and mobile fallback

**Files:**
- Create: `src/modules/system-handbook/components/ArchitectureMobileList.tsx`
- Modify: `src/modules/system-handbook/pages/SystemHandbookPage.tsx`
- Modify: `src/modules/system-handbook/data/architectureGraph.ts`
- Modify: `src/modules/admin/__tests__/system-overview.test.tsx`
- Modify: `src/index.css`

**Interfaces:**
- `ArchitectureMobileList` receives the same prepared graph and selection callbacks as the desktop surface, and renders the four layers as expandable sections.
- Toolbar state is local to `ArchitectureView` and feeds `buildArchitectureGraph`; URL state remains limited to selection/flow context.

- [ ] **Step 1: Write failing responsive/filter tests**

Add tests that search `order-service` leaves it visible while unrelated nodes are filtered, selecting `COD order` marks `order-service` highlighted, and a narrow viewport renders `Danh sách theo lớp` with `Client applications` and connection rows.

```tsx
it('filters the architecture and provides a mobile-readable fallback', async () => {
  const user = userEvent.setup();
  renderApp({ route: '/system-overview/architecture' });
  await user.type(screen.getByRole('textbox', { name: 'Tìm service, client, database...' }), 'order-service');
  expect(screen.getByRole('button', { name: /order-service.*Checkout/ })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /livestream-service/ })).not.toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Chọn flow để highlight' }));
  await user.click(screen.getByRole('option', { name: /COD order/ }));
  expect(screen.getByText('Node tham gia flow')).toBeInTheDocument();
});
```

Run:

```bash
npx vitest run src/modules/admin/__tests__/system-overview.test.tsx
```

Expected: FAIL because filter controls and mobile fallback do not exist.

- [ ] **Step 2: Implement query, layer, status, and flow-lens controls**

Use controlled input/select elements with Vietnamese labels. Keep status text visible in nodes. Flow lens options come only from `FLOW_EXPLORER_FLOWS`; choosing none clears highlight without changing route. `Vừa khung` calls React Flow `fitView`; `Đặt lại bố cục` reruns the layout helper.

- [ ] **Step 3: Implement the mobile layer list**

Render accordion sections for `Client`, `Gateway`, `Service`, and `Infrastructure`. Each row includes technical label, short role, status, active/highlight state, and click selection. Render connections as readable `source → target` rows with communication kind and click selection. Put the inspector below the list; do not require hover or canvas interaction.

- [ ] **Step 4: Add responsive CSS and accessibility details**

At the mobile breakpoint hide the React Flow canvas and show the list; ensure the toolbar wraps without horizontal overflow, text has at least 12px readable technical labels, focus rings are visible, and reduced motion disables layout/selection transitions.

- [ ] **Step 5: Run focused responsive tests**

Run:

```bash
npx vitest run src/modules/admin/__tests__/system-overview.test.tsx
```

Expected: PASS with existing and new Architecture tests.

- [ ] **Step 6: Commit responsive slice**

```bash
git add src/modules/system-handbook/components/ArchitectureMobileList.tsx src/modules/system-handbook/pages/SystemHandbookPage.tsx src/modules/system-handbook/data/architectureGraph.ts src/modules/admin/__tests__/system-overview.test.tsx src/index.css
git commit -m "feat(web): add architecture handbook filters and mobile view"
```

### Task 4: Browser QA and repository verification

**Files:**
- Modify: `docs/superpowers/plans/2026-08-27-architecture-handbook.md`

- [ ] **Step 1: Start or reuse the Vite dev server**

Run:

```bash
npm run dev -- --host 0.0.0.0
```

Use the existing local server on port `5173` when it is already serving this checkout; do not start a second server on the same port.

- [ ] **Step 2: Validate the desktop target flow in Browser/IAB**

Target flow: `/system-overview/architecture` → search/select `order-service` → inspect responsibilities/data owner → select `customer-app → gateway` edge → inspect communication detail → choose `COD order` flow lens.

Check page identity, non-blank content, no framework overlay, no relevant console errors, screenshot evidence, URL query updates, node/edge selection, fit view, and filter state.

- [ ] **Step 3: Validate the mobile fallback in Browser/IAB**

Use a mobile-sized viewport and verify the graph is replaced by `Danh sách theo lớp`, layers expand, connection list is readable, inspector appears below, and the page has no horizontal overflow or clipped technical labels.

- [ ] **Step 4: Run repository verification**

Run:

```bash
npm run verify:ci
```

Expected: lint, typecheck, all Vitest tests, action contract verification, and production build pass.

- [ ] **Step 5: Record validation and final diff**

Update this plan's Progress, Validation, and Result sections with the actual command results, screenshots/viewport evidence, and any intentionally untested browser state. Run `git diff --check` and `git status --short`.

- [ ] **Step 6: Commit verification record if changed**

```bash
git add docs/superpowers/plans/2026-08-27-architecture-handbook.md
git commit -m "docs(web): record architecture handbook verification"
```
