# Standalone preview

The preview bundle can be opened as a local HTML file; a Vite dev server is not
required.

```bash
npm run build:preview-static
open dist-preview-static/preview-static.html
```

The generated file uses hash routes, so these screens can be opened directly
from the same file:

- `preview-static.html#/preview/shopeefood/home?view=responsive`
- `preview-static.html#/preview/admin/orders?view=responsive`
- `preview-static.html#/preview/admin/chat?view=workbench`
- `preview-static.html#/preview/admin/restaurants?view=responsive`
- `preview-static.html#/preview/shopeefood/support?view=phone`
- `preview-static.html#/preview/restaurant/orders?view=responsive`
- `preview-static.html#/preview/shipper/map?view=workbench`

The JavaScript and CSS are inlined into `preview-static.html`. Remote images,
Google Fonts, and Mapbox tiles still require network access; the preview has
fallback states when those resources are unavailable.

The existing `npm run dev` routes remain available for development and are not
changed by this standalone entry.
