# Feature: Đối soát COD (Settlement)

> Trạng thái: backend/client contract đã rà soát; delivery-exception review bridge
> đã có code/unit proof nhưng runtime Gate B8 còn mở
> Service: `settlement-service` (`:8090`) · Cập nhật: 2026-08-08

## Phạm vi MVP

Settlement là sổ cái của luồng COD. MVP chỉ mở:

- kiểm tra nội bộ shipper có đủ ký quỹ trước khi Match gửi offer;
- ghi sổ khi nhận Kafka `delivery.completed`;
- các API đọc dành cho admin, có giới hạn tối đa 100 bản ghi, gồm queue refund
  chỉ-đọc để theo dõi case và manual-review status.
- customer có thể xem read-only lịch sử/trạng thái refund case của chính mình;
  Flutter hiển thị trong chi tiết đơn và màn hình lịch sử, không có action tạo,
  duyệt hay thực hiện hoàn tiền.

### Provider-neutral payment/refund/payout boundary

Settlement now owns a contract-only adapter seam under
`settlement-service/payment/contract`:

- payment/refund requests carry a stable operation ID, durable idempotency key,
  immutable order reference and canonical money value;
- `UNKNOWN` is distinct from `FAILED`, so a provider timeout cannot be treated
  as a successful or failed money movement;
- payout requests reference an existing Settlement ledger entry and a positive
  `RESTAURANT`/`SHIPPER` beneficiary; the contract itself never debits a
  balance;
- `PayOsProviderAdapter` is an unwired, in-process contract adapter. It has no
  Spring bean, credential, callback route or HTTP client in the MVP.

`PAYOUT_PROCESSING_ENABLED=false` and
`PAYOUT_PROVIDER=PAYOS` are explicit default-off configuration. Provider
execution, callback verification, reconciliation, admin financial mutation and
customer money UI remain gated follow-ups; this code is not proof of a real
PayOS payment or payout.

Thanh toán online/VNPay, API ví tự phục vụ, nạp/rút tiền và mutation của admin đều
đang hidden/off. Một sandbox return boundary có thể được route qua Gateway chỉ
khi `PAYMENT_CLIENT_API_ENABLED=true` và `PAYMENT_PROCESSING_ENABLED=true`; cả
hai mặc định `false`. Surface này chỉ gồm `POST /api/settlement/payments/create`
và `GET /api/settlement/payments/ref/{paymentRef}`, derive USER từ JWT và không
nhận entity/payer ID từ client. Vì `payment_orders` chưa lưu customer principal
và Order vẫn COD-only, boundary hiện trả lỗi 409 explicit cho create/read thay vì
tạo payment hoặc tiết lộ trạng thái: đây là proof cho gate/return handling, không
phải online-order E2E. VNPay callback, IPN và fake confirmation không đi qua
Gateway.

## Luồng chuẩn

1. Match gọi internal API bằng shared secret với `shipperId` và `codAmount` chính
   là `totalPrice` canonical của đơn.
2. Chỉ shipper có `depositBalance >= codAmount` mới được xét offer.
3. Khi giao thành công, Delivery phát `delivery.completed` với stable `eventId`,
   identity đầy đủ, `paymentMethod=COD` và các khoản tiền canonical.
4. Settlement validate payload và tạo receipt duy nhất theo event/order.
5. Trong cùng transaction, Settlement ghi bốn khoản:
   - CREDIT doanh thu net cho nhà hàng vào `EARNINGS`;
   - CREDIT phí giao cho shipper vào `EARNINGS`;
   - DEBIT tổng tiền mặt đã thu khỏi ví `DEPOSIT` của shipper;
   - CREDIT hoa hồng cho entity `SYSTEM`.
6. Nếu ký quỹ không còn đủ, toàn bộ receipt và ledger rollback; hệ thống không cho
   phép balance âm và Kafka record đi retry/DLT.

## Idempotency và lỗi

- Exact replay: ACK, không ghi lần hai.
- Cùng event ID nhưng payload khác: fail-closed.
- Cùng order dưới event ID khác: fail-closed.
- ID không dương, non-COD, thiếu tiền hoặc commission không khớp: không ACK-discard;
  lỗi được ném để retry/DLT.
- Unique business key của ledger và pessimistic balance lock là lớp bảo vệ DB.
- Settlement chỉ nhận event khi split phí canonical khớp: `shipperEarnings +
  shippingCommission = shippingFee` và `restaurantCommission +
  shippingCommission = totalPlatformEarnings`; payload sai bị retry/DLT trước khi
  tạo receipt hoặc ghi ledger.
  Race thật trên PostgreSQL vẫn phải rehearsal trong Gate B8.

## API đang hoạt động

| Boundary | Contract |
|---|---|
| Internal | `GET /api/settlement/internal/shippers/{shipperId}/cod-eligibility` |
| Admin read | `GET /api/settlement/admin/balances` |
| Admin read | `GET /api/settlement/admin/transactions` |
| Admin read | `GET /api/settlement/admin/transactions/pending` |
| Admin read | `GET /api/settlement/admin/revenue` |
| Admin read | `GET /api/settlement/admin/refunds?status=&limit=` |
| Admin read | `GET /api/settlement/admin/refunds/{refundId}` |
| Customer read | `GET /api/settlement/refunds/my?limit=` |
| Kafka | consume `delivery.completed` |
| Kafka | consume `order.cancelled` and `order.refund-eligible` |

Refund admin reads chỉ trả projection đã lọc của `refund_cases`; không có approve,
reject, retry, reverse ledger hay provider call. Cancellation source/reason code
được lưu cùng immutable snapshot; customer cancellation sau khi quán đã bắt đầu
chuẩn bị, admin exception và post-pickup outcomes đi vào `MANUAL_REVIEW`, còn
system/restaurant/no-shipper pre-pickup chỉ đủ điều kiện auto theo policy. COD
pre-capture luôn là `NO_REFUND_REQUIRED`. `REFUND_PROVIDER_PROCESSING_ENABLED`,
`REFUND_PROCESSING_ENABLED` và `REFUND_OUTBOX_RELAY_ENABLED` mặc định `false`.

Customer read bắt USER tại Settlement resource service, scope theo authenticated
JWT actor và chỉ
trả status-safe projection của case có sẵn. Flutter gửi limit 50, lọc case theo
order cho màn chi tiết và có lịch sử riêng; không được suy diễn amount/status này
thành guarantee provider đã trả tiền hoặc thêm client-side refund action.

Internal API không được route công khai qua Gateway. Admin read bắt role do
Settlement resource service derive từ JWT đã xác thực JWKS. Danh sách admin hiện
cap 100 và chưa có pagination contract.

## API hidden/off

- VNPay callback/IPN, fake confirm và legacy internal payment mutation/query
- `/api/settlement/payments/**` ngoài hai exact sandbox routes được gate ở trên
- `/api/settlement/balances/**`
- `/api/settlement/transactions/**`
- mutation `/api/settlement/admin/transactions/**`

Hai endpoint debug `recalculate` đã bị xóa vì không có caller và thuật toán dựng
lại không mô hình hóa đúng trạng thái pending/failed/reversed.

## Dữ liệu local và phần còn mở

MVP local dùng `backend_delivery/scripts/seed-settlement.sql` để seed ký quỹ cho
shipper test; đây không phải API production. Còn phải chứng minh PostgreSQL
concurrent replay, Kafka crash/restart/DLT và COD E2E trước Gate B8. Refund sau
settlement, withdrawal, top-up và payment online không thuộc MVP hiện tại.
Runbook vận hành cho refund/cancellation nằm tại
[`refund-workflow.md`](../../../runbooks/refund-workflow.md);
  provider refund thật vẫn là T7.

## Post-pickup delivery exception boundary

Delivery có thể phát `delivery.exception.reported` khi shipper báo giao thất bại
sau pickup. Event mang immutable identity, trạng thái post-pickup và snapshot tiền
do Delivery sở hữu; `discountAmount` được kiểm tra theo
`subtotalPrice + shippingFee - totalPrice`, trong đó `shippingFee` là gross
shipping fee. Settlement listener chỉ nhận event đầu
`DELIVERY_EXCEPTION_REPORTED/RETRY_AVAILABLE`, tạo một
`DELIVERY_DISPUTE + ORDER_TOTAL` case ở `MANUAL_REVIEW` và không enqueue provider
outbox, kể cả khi provider-processing khác được bật. Update `RETURNING`,
`RETURNED`, `RESOLVED` chỉ ACK/no-op ở listener này.

Capability được bật độc lập bằng
`SETTLEMENT_DELIVERY_EXCEPTION_PROCESSING_ENABLED`, mặc định `false`. Topic
`delivery.exception.reported` phải được provision cùng source-matched retry/DLT
target trước khi bật; provider refund thật và quyết định approve vẫn là phase sau.
