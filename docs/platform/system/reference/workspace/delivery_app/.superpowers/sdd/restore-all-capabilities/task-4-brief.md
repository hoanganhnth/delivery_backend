### Task 4: VNPay sandbox payment

- Add the Gateway-owned payment contract, backend sandbox adapter, explicit capability flags, and Flutter WebView/deep-link return handling with idempotent status refresh.
- Preserve COD behavior when the flag is off and prove both success and cancel/failure paths.

## Binding authority and safety decisions

- Backend settlement/payment is currently an existing but hidden graph: `PaymentController` and `VnPayProvider` are conditional on `PAYMENT_PROCESSING_ENABLED`, the Gateway has no payment route, and the canonical inventory marks online payment and callbacks hidden/default-off pending ownership, callback-origin, credential, reconciliation, and provider proof. Preserve that default.
- The new customer-facing edge must be Gateway-only and independently gated by `PAYMENT_CLIENT_API_ENABLED=false` by default; service processing must remain `PAYMENT_PROCESSING_ENABLED=false` by default. Do not route fake confirmation or IPN to production clients, and do not enable any payment flag in staging/prod manifests.
- The current order contract remains COD-only (`OrderValidationService` rejects non-COD). Do not silently allow `ONLINE` in the existing create-order path or claim a real paid-order E2E. The restored payment surface is a sandbox/return-flow boundary until an order-payment ownership contract exists.
- Flutter must add an explicit `VNPAY_PAYMENT_ENABLED` build-time flag defaulting to false. With it off, the existing COD UI, request payload, and idempotency behavior remain unchanged. All HTTP goes through the authenticated Gateway Dio; no service port, `Internal-Token`, or callback URL is hard-coded in the client.
- Model return handling as a one-shot/idempotent state transition: parse only the provider callback identity/status, refresh canonical status through the Gateway by payment reference, ignore duplicate callbacks after terminal state, and never create/clear an order or claim success from the URL alone. Tests must cover provider success, cancel/failure, duplicate callback, malformed callback, and status-refresh failure.
- Use the existing VNPay sandbox provider/DTO vocabulary where compatible (`/api/settlement/payments/create`, `/api/settlement/payments/ref/{paymentRef}`, `PaymentOrderResponse`); if a new customer-scoped adapter is required, it must derive payer identity from the JWT and fail closed for unsupported ownership instead of trusting a client-supplied user/entity ID.
