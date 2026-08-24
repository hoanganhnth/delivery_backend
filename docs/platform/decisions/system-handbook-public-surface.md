# Decision: Public system handbook surface

Date: 2026-08-22

## Status

Accepted for the Delivery Web handbook.

## Decision

`/system-overview` is a standalone public, read-only technical and product
handbook. It is not part of the admin or restaurant portal and does not require
an authenticated session or live backend access.

The handbook may catalog source capabilities, services and API families that
are active, protected, internal, dev-only, gated or experimental. Every entry
must carry an explicit status/classification. Internal, dev-only and
experimental entries are documentation metadata only; they must not be shown
as public Gateway contracts.

The handbook must not expose secrets, credentials, private keys, customer data,
raw production payloads or operational access instructions. Source references
are shown at file-and-section granularity.

## Authority and maintenance

- Product behavior: `backend_delivery/docs/platform/product/overview.md` and
  client feature inventories.
- Service ownership: `backend_delivery/docs/platform/system/service-catalog.md`.
- API source map: `backend_delivery/docs/platform/system/api/http-contract.json`
  and the backend HTTP inventory.
- Security and exposure policy: `backend_delivery/docs/platform/system/security.md`
  and this decision.

The generated handbook snapshot is checked for drift from these sources. A
controller or schema existing in source is not sufficient evidence that a
capability is public or active.
