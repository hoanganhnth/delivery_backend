# Customer desktop/mobile visual parity

## Outcome and authority

User approved planning and implementation on September 8, 2026. The existing
ShopeeFood mobile preview is the visual reference: red-orange emphasis, white
surfaces, neutral background, food photography, compact typography and rows.
Desktop expands this experience into a wide layout using the same components.

## Sequence

1. Add an adaptive desktop presentation to the existing interactive preview;
   retain explicit phone mode and all in-memory ordering/account behavior.
2. Align production customer colors, typography and surface styling; replace
   its brown landing hero with a compact food-discovery composition.
3. Validate typecheck/build, focused existing tests and desktop/mobile browser
   renderings including ordering, account screens and dialogs.

## Acceptance

Desktop preview has top navigation, wide food grids, readable forms and order
tracking; mobile retains its current layout and bottom navigation. All preview
routes support desktop. Production continues to use existing API adapters.
No fake preview data is introduced into production. No full E2E requested.

## Recovery and scope

Changes are confined to customer presentation and preview. Existing dirty
admin/restaurant changes are preserved. Roll back only this change's files or
hunks; no database or session recovery is needed. Admin/restaurant redesign is
outside this customer desktop/mobile parity pass.

## Progress

- Inspected current preview, live storefront, routes and existing screenshots.
- Implemented adaptive desktop preview and preserved explicit phone/workbench modes.
- Production customer screens share the red-orange/neutral visual palette and
  compact surface treatment. Home now uses a peach food-discovery banner; its
  photo comes from the real API catalog when available.
- Typecheck and production build passed. Scoped ESLint and diff whitespace check
  passed. Existing focused preview/customer suite: 5 files, 20 tests passed;
  public-catalog heading assertion updated for the new heading copy.
- Chromium rendered 14 preview routes at 390/768/1440px without viewport
  horizontal overflow. Inspected desktop home, restaurant, tracking and checkout
  screenshots. Desktop add → cart → checkout → place completed without page errors.
- Production home checked at 320/390/768/1440px without horizontal overflow.
  Backend unavailable locally; its observed error state works, while populated
  production-data visual verification remains limited. No live order was placed.
- Full E2E not run. Preview remains development-only, with fixture state reset
  on refresh. Admin/restaurant portals retain their existing design.
