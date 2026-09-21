# Customer mobile preview

Run the normal Vite development server and open:

- `/preview/shopeefood/home`: responsive desktop/tablet/mobile experience using the same screens and state.
- `/preview/shopeefood/home?view=workbench`: screen, viewport, catalog scenario and order-state controls.
- `/preview/shopeefood/home?view=phone`: clean phone view, without preview controls. The flag survives navigation.

Above 700px the default view uses a desktop header, wide food grids, restaurant
columns and order tracking alongside order information. Phone mode keeps the
original narrow preview. These modes share the same in-memory state and dialogs.

Only available under `import.meta.env.DEV`. Production builds exclude the preview. No backend needed. Catalog image URLs require internet; unavailable images have a fallback. The preview does not call business APIs or read/write production session/cart storage. State lives in memory; refresh resets it.

## Try it

The Orders tab starts with six example orders: pending, preparing, driver going to restaurant, delivering, delivered and cancelled. Open any order for its timeline and labelled schematic map. Run/pause the simulation or advance manually; each automatic stage takes three seconds. Completion/cancellation offers replay. Navigating away disposes the timer. No GPS, driver calls or messages are sent. Reset restores the six fixtures.

1. Choose the Home delivery address, open a restaurant, select a dish and add it.
2. Open cart and select delivery. Checkout initially follows Home.
3. Override the checkout address and optionally apply the sample shipping voucher.
4. Place a mock order; return Home to verify its address was not changed.
5. Use the workbench order selector for delivering/delivered/cancelled states.
6. Use screen navigation to review login, registration, verification and address management. Deep-linking to checkout/order detail without an order shows an actionable empty state.

Data: six restaurants and 24 photos from the existing Hanoi Grab catalog. Prices, menu translations, ratings, ETA, promotions and contact information are synthetic preview data. No Shopee authentication occurs; credentials entered into the simulated forms are not persisted.

## Fidelity ledger

| Screens | Evidence | Current limitation |
| --- | --- | --- |
| Home/search/restaurant/item/cart/checkout/voucher | Public standalone ShopeeFood imagery and older ordering guides linked in execution plan | Reconstructed; not pixel-verified against current Shopee embedded iPhone UI. Banner and data differ. |
| Orders/order detail | Public Sapo order guide | Status layout inferred; map is a labelled static illustration. |
| Address management and contextual selection | Existing Delivery address rules | Layout inferred; no matching current Shopee reference. |
| Login/register/email verification | Existing Delivery screen coverage | Simulated forms; email verification is not asserted to match Shopee. |

The reference ledger and verification evidence live in `docs/plans/active/shopeefood-mobile-preview.md`. Do not present this as a verified exact clone or wire these mock adapters into production.
