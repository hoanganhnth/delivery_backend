# Voucher and flash-sale checkout rollout

## Preconditions

1. Back up `order_db`, `promotion_db`, and `flashsale_db`.
2. Apply and verify the current Order/Promotion/Flash-sale migrations,
   including Order V16 and Promotion V7 stacking foundations.
3. Run module tests, PostgreSQL concurrency tests, Gateway route/security tests,
   Compose config validation, Flutter OFF/ON tests, and Web verify.
4. Confirm Gateway exposes Order preview/create, voucher wallet, and public flash
   catalog, while internal quote/reservation paths remain absent.
5. Confirm dashboards/alerts exist for reservation age/state, outbox PENDING/DEAD,
   release failures, stock/counter mismatch, and settlement rejection.

### Preflight không dùng Docker

Khi chưa có local runtime hoặc muốn tránh khởi động Docker, có thể chuẩn bị
release và kiểm tra manifest bằng các lệnh read-only/render-only sau:

```bash
mvn -pl api-gateway,promotion-service,order-service,delivery-service,settlement-service \
  -am package -DskipTests
node deploy/kubernetes/generate.mjs --check
bash scripts/verify-kubernetes-manifests.sh
kubectl kustomize deploy/kubernetes/overlays/staging-template >/tmp/delivery-staging.yaml
```

Kết quả phải xác nhận 20 workload private, không có `Ingress`, `LoadBalancer`,
`NodePort` hoặc plaintext `Secret`, và bốn stacking controls vẫn
fail-closed. Các lệnh trên không gọi `docker` và không `kubectl apply`.

## Enable order

Enable one environment/canary at a time:

1. `PROMOTION_CHECKOUT_ENABLED=true`
2. `FLASHSALE_CHECKOUT_ENABLED=true`
3. `PROMOTION_OUTBOX_RELAY_ENABLED=true`
4. `FLASHSALE_OUTBOX_RELAY_ENABLED=true`
5. `ORDER_VOUCHER_CHECKOUT_ENABLED=true`
6. `ORDER_FLASHSALE_CHECKOUT_ENABLED=true`
7. For stacked voucher canary, also set
   `ORDER_VOUCHER_STACKING_ENABLED=true`,
   `PROMOTION_STACKING_ENABLED=true`, and the same comma-separated stable
   principal IDs in `ORDER_VOUCHER_STACKING_CANARY_PRINCIPALS` and
   `PROMOTION_STACKING_CANARY_PRINCIPALS`. The allowlist must never be empty.
8. Build Flutter with `--dart-define=VOUCHER_STACKING_ENABLED=true` for the
   same canary only after the capability endpoint returns enabled; legacy
   single voucher still uses `--dart-define=VOUCHER_CHECKOUT_ENABLED=true`.
   Flash Sale continues to use
   `--dart-define=FLASHSALE_CHECKOUT_ENABLED=true` and must not be enabled in
   the same checkout scenario.

`FLASHSALE_MERCHANT_REGISTRATION_ENABLED` is a separate ownership rollout and
must stay false unless its Gateway route and merchant UI are explicitly shipped.

## Canary checks

- Normal, voucher, and flash checkout totals match Order snapshots.
- Duplicate voucher and exhausted stock return business failure with no partial
  Order/outbox/counter mutation.
- Cancel, restaurant reject, ambiguous timeout, and payment failure release.
- Exact `order.created`/`order.cancelled` replay changes no counter twice.
- `delivery.completed.totalPrice` reconciles and settlement creates one receipt.

## Local retained-volume rehearsal

After packaging the affected services, run:

```bash
bash scripts/verify-voucher-flashsale-checkout.sh
```

The script is intentionally not a read-only smoke test. It rebuilds and
recreates `order-service`, `promotion-service`, and `flashsale-service`, then
writes uniquely named ADMIN/customer/owner/shipper, voucher, campaign, item,
order, reservation, delivery, and settlement fixtures into the current Compose
volumes. It never deletes or recreates a volume.

The EXIT trap first disables new Order voucher/flash selection, cancels or
directly releases only unfinished reservations belonging to the unique fixture,
drains Promotion/Flash outboxes, marks the fixture voucher/campaign inactive,
and finally recreates the three services with every Task 21 flag set to `false`.
Run it only after explicitly approving those container and retained-database
mutations on the current local stack.

If a fixture reservation cannot reach a terminal state or any reservation
outbox remains `PENDING`/`DEAD`, the trap fails closed: Order checkout remains
disabled, while Promotion/Flash checkout and relays remain enabled for operator
recovery. It does not hide the failure by turning off the only recovery path.

## Rollback

1. Disable Flutter flags/releases first so no new selection is offered.
2. Disable Order voucher/flash flags.
3. Keep Promotion/Flash-sale checkout and relays running until all existing
   `RESERVED` records are committed, released, or expired and outboxes drain.
4. Disable relays, then service checkout flags.
5. Do not decrement counters manually. Reconcile each active reservation against
   its lines/wallet and replay the recorded idempotent release if necessary.

Rollback does not remove migrations or reservation audit rows.

## Reconciliation queries

Inspect, per service, `RESERVED` rows older than 15 minutes, outbox rows in
`PENDING`/`DEAD`, voucher `used_quantity` against non-released reservations, and
flash `sold_quantity` against active/committed reservation lines. Any mismatch
keeps checkout disabled until corrected through an audited idempotent transition.
