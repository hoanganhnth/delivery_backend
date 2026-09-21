# Portal preview

DEV-only previews for the two operational portals. The preview renders the
same production pages, layouts, providers, dialogs, forms, and responsive CSS
used by the authenticated web app. Only the dependency ports are replaced by
deterministic in-memory fixtures, so the screens can be reviewed without a
running backend or an authenticated session.

## Admin

- `/preview/admin/dashboard`
- `/preview/admin/orders`
- `/preview/admin/restaurants`
- `/preview/admin/shippers`
- `/preview/admin/ratings`
- `/preview/admin/coupons`
- `/preview/admin/flash-sales`
- `/preview/admin/catalog-import`

## Restaurant

- `/preview/restaurant/dashboard`
- `/preview/restaurant/orders`
- `/preview/restaurant/menu`
- `/preview/restaurant/profile`
- `/preview/restaurant/reviews`
- `/preview/restaurant/vouchers`
- `/preview/restaurant/catalog-import`
- `/preview/restaurant/livestream`

Append `?view=responsive` (or omit the query string) for the normal desktop
layout. `?view=phone` constrains the production page to a phone viewport, and
`?view=workbench` adds screen/theme/reset controls above the page.

All data comes from the deterministic fixtures in `portalPreviewData.ts` and
the fixture-backed ports in `portalPreviewDependencies.ts`. Changes such as
order status, menu availability, profile edits, dialogs, and toasts are kept in
the current preview tab only; no request, browser storage write, or production
mutation is made. This is a UI/interaction preview, not a replacement for an
authenticated integration or end-to-end test.
