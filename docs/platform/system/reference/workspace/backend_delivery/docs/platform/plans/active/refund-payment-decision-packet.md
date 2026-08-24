# Decision Packet: Automatic Refund và Online Payment

Ngày: 2026-08-01 (policy approved 2026-08-02)  
Trạng thái: Approved MVP policy — T6 boundary implemented; T7 blocked  
Phạm vi: policy authority cho T6 refund boundary; provider execution và online
payment vẫn hidden/default-off.

## 1. Mục đích

Tài liệu này là authority cho policy MVP bảo thủ đã được user duyệt để triển khai
T6. Phần provider execution/online payment vẫn cần authority vận hành riêng của
T7; các mặc định an toàn trong tài liệu không tự mở provider hoặc client money UI.

## 2. Nguồn sự thật hiện tại

| Chủ đề | Hiện trạng đã xác minh | Nguồn |
|---|---|---|
| Phạm vi Settlement MVP | Chỉ ghi sổ COD khi `delivery.completed`; online payment, self-service wallet và admin financial mutations đang off | [Settlement feature](../../product/features/settlement.md), [Settlement workflow](../../../workflows/settlement_finance_flow.md) |
| Pricing | Order lưu snapshot `subtotalPrice`, `discountAmount`, `shippingFee`, `totalPrice`, `paymentMethod` và reservation IDs | [`Order`](../../../../order-service/src/main/java/com/delivery/order_service/entity/Order.java), [Task 21](../completed/task-21-voucher-flashsale-checkout.md) |
| Hủy đơn | Customer/restaurant owner được hủy trước pickup; admin bypass actor check nhưng vẫn chịu state-transition rules; trạng thái `PICKED_UP`/`DELIVERING` không có customer cancellation path hiện tại | [`OrderStatus`](../../../../order-service/src/main/java/com/delivery/order_service/entity/OrderStatus.java), [`OrderServiceImpl`](../../../../order-service/src/main/java/com/delivery/order_service/service/impl/OrderServiceImpl.java) |
| Cancellation event | `order.cancelled` mang order/user/restaurant, trạng thái trước đó, actor, reason, shipper và voucher/flash reservation IDs | [`OrderCancelledEvent`](../../../../order-service/src/main/java/com/delivery/order_service/dto/event/OrderCancelledEvent.java), [`OrderEventPublisher`](../../../../order-service/src/main/java/com/delivery/order_service/service/OrderEventPublisher.java) |
| Voucher/flash-sale | State machine `RESERVED -> COMMITTED | RELEASED | EXPIRED`; `COMMITTED -> RELEASED` chỉ dành cho cancellation/payment failure trước fulfillment; checkout xếp tối đa một voucher mỗi lớp `SHOP_DISCOUNT`, `PLATFORM_DISCOUNT`, `FREESHIP`, còn voucher và flash-sale không stacking | [Decision 0004](../../decisions/0004-voucher-stacking-shopee-policy.md) |
| COD settlement | Khi delivered, Settlement ghi receipt và bốn ledger entries trong một transaction; exact replay/conflict replay được fail-closed | [`DeliveryCompletedEvent`](../../../../settlement-service/src/main/java/com/delivery/settlement_service/dto/event/DeliveryCompletedEvent.java), [Settlement workflow](../../../workflows/settlement_finance_flow.md) |
| Refund building blocks | `TransactionReason` đã có `REFUND_RECEIVED`, `REFUND_ISSUED`, `COD_REFUND`; transaction bất biến và reversal hiện là admin operation tạo entry đối ứng | [`Transaction`](../../../../settlement-service/src/main/java/com/delivery/settlement_service/entity/Transaction.java), [`TransactionService`](../../../../settlement-service/src/main/java/com/delivery/settlement_service/service/TransactionService.java) |
| Online payment graph | Payment controller/provider/event graph tồn tại nhưng chỉ khởi động khi `PAYMENT_PROCESSING_ENABLED=true`; default hiện là `false` | [`PaymentController`](../../../../settlement-service/src/main/java/com/delivery/settlement_service/controller/PaymentController.java), [`PaymentServiceImpl`](../../../../settlement-service/src/main/java/com/delivery/settlement_service/service/impl/PaymentServiceImpl.java), [Settlement config](../../../../settlement-service/src/main/resources/application.properties) |
| Payment failure | Payment failure có thể phát `payment.failed`; Order chuyển sang `CANCELLED` và phát `order.cancelled`; chưa có refund trigger tự động | [`PaymentEventPublisher`](../../../../settlement-service/src/main/java/com/delivery/settlement_service/service/PaymentEventPublisher.java), [`OrderEventServiceImpl`](../../../../order-service/src/main/java/com/delivery/order_service/service/impl/OrderEventServiceImpl.java) |

## 3. Luồng hiện tại và ranh giới cần giữ

```mermaid
sequenceDiagram
    participant C as Customer/Admin
    participant O as Order
    participant R as Promotion/Flash-sale
    participant D as Delivery
    participant S as Settlement
    participant P as Payment Provider

    C->>O: create order with server-owned monetary snapshot
    O->>R: reserve/commit or release reservation
    O->>D: order.created / delivery lifecycle
    D->>S: delivery.completed (COD only)
    S->>S: receipt + ledger transaction
    C->>O: cancellation or payment failure
    O->>R: release reservation where policy allows
    O->>S: order.cancelled / order.refund-eligible (flagged boundary)
    S->>S: durable case + immutable snapshot + idempotency
    Note over S,P: provider instrument remains gated; admin queue is read-only
    Note over C,P: Online provider remains hidden/default-off
```

Các điểm không được suy diễn thành policy mới:

- Có `REFUND_*`/`COD_REFUND` enum không có nghĩa là hệ thống đã được phép tự
  động hoàn tiền.
- Admin `reverse` là thao tác đối soát thủ công trên ledger, không phải provider
  refund và không giải quyết allocation của voucher, phí giao hoặc commission.
- `payment.failed` hiện là tín hiệu hủy order/giải phóng reservation; không phải
  bằng chứng provider đã hoàn tiền.
- `delivery.completed` hiện chỉ chấp nhận COD trong MVP. Không thêm ONLINE vào
  event/Settlement khi chưa có quyết định T7.

## 4. Benchmark tham khảo từ GrabFood và ShopeeFood

> Reviewed: 2026-08-01. Đây là các chính sách công khai để tham khảo UX và
> control model, không phải authority pháp lý hay policy tự động của hệ thống.
> Nội dung/market policy của các nền tảng có thể thay đổi.

### 4.1 GrabFood

- Điều khoản GrabFood nói đơn đã được xác nhận nhìn chung không được người dùng
  hủy; nếu hủy sau khi quán bắt đầu chuẩn bị hoặc khách không nhận/không liên lạc
  được, khách vẫn có thể phải thanh toán toàn bộ đơn. [Grab Terms of Service —
  Food](https://www.grab.com/vn/terms-policies/transport-delivery-logistics/)
  (mục 1.2, dòng 488–490).
- Refund được tách thành case có lý do như mất/hư hỏng khi vận chuyển, sai/thiếu
  món, hoặc đã thanh toán nhưng không giao. Người dùng phải gửi yêu cầu trong
  vòng 48 giờ; Grab có bước xác minh và nêu thời hạn xử lý/hoàn tiền riêng.
  [Grab Terms of Service — Refund](https://www.grab.com/vn/terms-policies/transport-delivery-logistics/)
  (mục 30.4, dòng 455–470).

### 4.2 ShopeeFood

- Hướng dẫn thanh toán công khai của ShopeeFood/Now mô tả tiền trả trước được
  hoàn về credit nội bộ khi khách hủy sau khi thanh toán; chênh lệch giảm giá
  cũng được hoàn về credit. Đây là bằng chứng về mô hình wallet fallback, không
  phải contract API hiện tại của project.
  [ShopeeFood payment guide](https://shopee.shopeefood.vn/payment-guide)
  (dòng 165–180).
- ShopeeFood tách bồi hoàn cho quán khỏi refund cho khách: đơn đã chuẩn bị và
  không thể bán lại cần báo cáo trong 24 giờ, có hóa đơn/hình ảnh; thời gian trả
  cho quán có thể 5–7 ngày làm việc hoặc lâu hơn tùy phương thức.
  [ShopeeFood canceled-order compensation](https://merchant.shopeefood.vn/edu/article/dieu-kien-hoan-tien-don-hang-huy),
  [ShopeeFood compensation request](https://merchant.shopeefood.vn/edu/article/huong-dan-gui-yeu-cau-hoan-tien-don-huy).
- ShopeeFood hướng dẫn quán không tự bấm hủy thay khách mà chuyển khách tới
  Trung tâm trợ giúp; điều này giữ actor/reason/audit của cancellation ở một
  boundary trung tâm.
  [ShopeeFood cancellation guidance](https://merchant.shopeefood.vn/edu/article/quan-bi-khachtai-xe-nho-huy-don-dum-thi-phai-lam-the-nao).

### 4.3 Mẫu số chung rút ra

1. Không auto-refund mọi cancellation; cutoff gắn với việc quán bắt đầu chuẩn bị
   và delivery progress.
2. Refund customer và compensation cho restaurant/shipper là hai case tiền khác
   nhau, có actor, evidence và ledger khác nhau.
3. Thanh toán trả trước cần refund về payment instrument hoặc wallet đã chọn;
   COD không thể được xử lý như một provider refund.
4. Defect/dispute sau khi chuẩn bị hoặc giao cần support window, evidence,
   manual review và partial/full decision.
5. Refund phải có audit và reconciliation; không nên chỉ đổi trạng thái Order.

## 5. Policy MVP bảo thủ — đã được duyệt cho T6

Đây là policy được suy ra từ benchmark GrabFood/ShopeeFood và constraint hiện tại
của project, đã được user duyệt để làm authority cho T6. T7 vẫn bị khóa.

| ID | Đề xuất |
|---|---|
| D1 | Tự động xử lý cancellation do hệ thống/nhà hàng/không tìm được shipper trước `PICKED_UP`; customer cancellation sau khi quán bắt đầu chuẩn bị không auto-refund; sau `PICKED_UP`/`DELIVERING`/`DELIVERED` chỉ mở manual dispute. Defect claim cho phép trong 48 giờ. |
| D2 | Auto chỉ cho deterministic system failure; restaurant/customer dispute, sau pickup và delivered do admin review. |
| D3 | COD: trước khi thu tiền không tạo customer refund; chỉ release reservation. Sau khi đã thu/settle, reverse phải là manual-approved ledger case và không cho balance âm. |
| D4 | Online: refund về payment instrument gốc; wallet fallback chỉ mở nếu product duyệt. Provider vẫn hidden/off cho tới T7. |
| D5 | Refund dùng immutable Order monetary snapshot và số tiền thực đã capture; voucher/flash-sale xử lý bằng release/restore reservation theo policy Task 21, không tự biến discount thành tiền mặt. |
| D6 | State đề xuất: `REQUESTED -> PROCESSING -> SUCCEEDED \| PARTIAL \| FAILED \| MANUAL_REVIEW`; không tự coi `CANCELLED` là `REFUNDED`. |
| D7 | Stable `refundId`; uniqueness theo `(orderId, trigger, component)`; exact replay trả kết quả cũ, payload conflict fail-closed; mọi event có `eventId`/`schemaVersion`. |
| D8 | Retry chỉ lỗi tạm thời; provider `UNKNOWN`, amount mismatch và exhausted retry chuyển `MANUAL_REVIEW`/DLQ; có reconciliation job trước khi retry mù. |
| D9 | Audit actor/source/reason, monetary breakdown, previous/next state, provider reference, attempt, event/correlation ID; không lưu secret/card data. |
| D10 | Customer thấy `REQUESTED/PROCESSING/SUCCEEDED/PARTIAL/FAILED/MANUAL_REVIEW`; admin thấy queue, evidence, retry và reconciliation; restaurant/shipper chỉ nhận thông tin cần thiết. |
| D11 | Flag rollout theo backend → settlement → trigger → Gateway/client → provider; tất cả default-off, rollback không xóa ledger bất biến. |

Nếu product owner duyệt nguyên bảng này, chỉ cần ghi “Duyệt policy MVP bảo thủ
ở mục 5” và nêu ngoại lệ (nếu có). Khi đó bảng này mới được chuyển thành
authority cho T6; online provider vẫn cần T7 riêng.

## 6. Câu hỏi còn mở cho T7/ngoại lệ

Các câu hỏi dưới đây chỉ áp dụng khi mở provider, wallet fallback, dispute sau
fulfillment hoặc ngoại lệ ngoài policy MVP. Nếu chọn “khác”, phải mô tả định
lượng, actor chịu trách nhiệm và trạng thái hệ thống tương ứng.

### D1 — Refund trigger và eligibility

Chọn chính xác các trường hợp:

- `PENDING`/`CONFIRMED` bị customer hủy.
- Restaurant reject hoặc hết thời gian confirm.
- `FINDING_SHIPPER`/`WAIT_SHIPPER_CONFIRM` không tìm được shipper.
- `ASSIGNED` bị hủy trước pickup.
- `PICKED_UP` hoặc `DELIVERING` bị hủy bởi admin/exception.
- `DELIVERED` dispute trong một khoảng thời gian cụ thể.
- Online payment failure trước fulfillment.
- Provider timeout/unknown status.

Với từng trigger, ghi: eligible hay không, cutoff time, actor hợp lệ, reason
code bắt buộc và có cần evidence/manual review hay không.

### D2 — Quyền phê duyệt

Chọn một mô hình cho từng trigger:

1. Tự động hoàn toàn theo rule engine.
2. Customer request, admin duyệt.
3. Restaurant duyệt trong cửa sổ giới hạn.
4. Tự động dưới ngưỡng tiền; admin duyệt trên ngưỡng.
5. Không refund tự động; chỉ tạo manual case.

Phải chỉ rõ ai được override, ai không được tự hoàn tiền và audit actor nào được
ghi nhận.

### D3 — COD

COD không có provider charge để gọi refund. Cần chọn riêng:

- Hủy trước pickup: chỉ release reservation hay có thêm customer credit?
- Hủy sau pickup trước delivered: xử lý tiền mặt đã/ chưa thu thế nào?
- Sau khi `delivery.completed`: reverse những ledger entry nào, và từ wallet nào?
- Nếu shipper đã nhận/đã nộp tiền: ai chịu âm tạm thời nếu balance không đủ?
- Refund cho customer bằng tiền mặt, wallet credit, bank transfer hay manual case?
- Có cho phép tự động tạo balance âm không? Mặc định an toàn hiện tại là không.

### D4 — Online payment

Chọn provider và instrument:

- Provider refund API (sync hoặc async).
- Wallet credit nội bộ.
- Bank transfer/manual reconciliation.
- Không hỗ trợ refund tự động ở phase đầu.

Phải chỉ rõ provider refund reference, signature/callback, timeout, trạng thái
`UNKNOWN`, cách query lại provider và điều kiện coi là đã hoàn thành. Không dùng
Fake provider để suy diễn hành vi production.

### D5 — Thành phần tiền được hoàn

Chốt công thức cho `refundAmount` và từng component:

| Component | Câu hỏi phải chốt |
|---|---|
| Món ăn/subtotal | Hoàn toàn bộ hay trừ phần đã sử dụng? |
| Shipping fee | Hoàn toàn bộ, một phần hay không hoàn theo từng trạng thái? |
| Voucher | Hoàn tiền theo giá trị discount hay chỉ release quota? |
| Flash-sale | Hoàn theo snapshot giá tại order hay giá hiện tại? |
| Restaurant/platform split | Đảo ledger theo gross hay theo net allocation? |
| Commission | Đảo toàn bộ, giữ lại theo công sức đã phát sinh hay admin quyết định? |
| Rounding/currency | Đơn vị tiền, scale và quy tắc làm tròn là gì? |

Không được để client tự tính số tiền. Công thức phải dùng snapshot immutable của
Order và có reconciliation với ledger/provider.

### D6 — Refund state machine

Chọn tên và transition chính thức. Một tập ứng viên để product owner cân nhắc:

```text
REQUESTED -> ELIGIBLE -> PROCESSING -> SUCCEEDED
                              ├------> PARTIAL
                              ├------> FAILED
                              └------> MANUAL_REVIEW
```

Phải chốt:

- Có state `REJECTED`, `CANCELLED`, `UNKNOWN`, `REVERSED` hay không.
- Refund request có immutable monetary snapshot không.
- Một order được bao nhiêu refund attempt/component.
- Có cho phép refund sau settlement không.
- Tác động của refund đối với Order status (`CANCELLED`, `REFUND_PENDING`,
  `REFUNDED`, `PARTIALLY_REFUNDED`) và Delivery status.

### D7 — Identity và idempotency

Phải chốt stable identity cho cả request và provider attempt:

- `refundId` có phải business key chính không?
- Có uniqueness theo `(orderId, trigger, component)` không?
- Exact replay trả ACK/hiện trạng nào?
- Cùng identity nhưng amount/payload khác xử lý fail-closed thế nào?
- Provider retry dùng cùng merchant reference hay tạo attempt reference mới?
- Event có `eventId`, `eventType`, `schemaVersion`, `occurredAt` và correlation ID
  nào?

Không dùng timestamp đơn lẻ làm idempotency key.

### D8 — Retry, DLQ và reconciliation

Phải chốt:

- Lỗi nào retry được, lỗi nào poison/manual.
- Số lần retry, backoff và DLT topic.
- Process crash sau provider success nhưng trước ACK xử lý thế nào.
- Scheduled reconciliation query provider bao lâu một lần.
- Ai xử lý unmatched refund, amount mismatch và provider `UNKNOWN`.
- Có emergency kill switch để dừng trigger mới nhưng không làm mất request đang
  `PROCESSING` hay không.

### D9 — Audit, bảo mật và dữ liệu nhạy cảm

Audit tối thiểu cần product/security duyệt:

- order/refund/payment/provider reference;
- actor ID, role, source (`AUTO`, `CUSTOMER`, `RESTAURANT`, `ADMIN`, `SYSTEM`);
- before/after state;
- immutable monetary breakdown;
- reason code và evidence reference;
- retry/attempt/provider response code;
- correlation ID và event ID.

Không lưu secret, full card data, signing key hoặc raw provider payload nếu policy
retention chưa cho phép. Nếu lưu callback payload, phải xác định masking và TTL.

### D10 — Notification và UI

Chốt các trạng thái hiển thị cho:

- customer app: refund requested/processing/success/partial/failed/manual;
- admin Web: queue, approve/reject, retry, reconcile, audit;
- restaurant/shipper: chỉ những thông tin cần biết;
- notification channel: in-app, email, FCM, WebSocket;
- nội dung không được tiết lộ provider secret hoặc dữ liệu nhạy cảm.

### D11 — Rollout và rollback

Phải chốt owner và thứ tự flag:

1. DB/schema compatibility.
2. Settlement consumer/ledger logic.
3. Order/Saga trigger.
4. Gateway/client visibility.
5. Provider call.

Rollback phải dừng trigger mới, giữ request đã tạo để reconcile, không xóa ledger
hoặc sửa transaction bất biến. Cần xác định cách replay sau khi deploy lại.

## 7. Bảng lựa chọn đã được duyệt

| ID | Quyết định | Lựa chọn đã chọn | Owner | Ngày | Ghi chú/authority |
|---|---|---|---|---|---|
| D1 | Trigger và eligibility | Hủy do system/restaurant/no-shipper trước `PICKED_UP` đi qua refund boundary; COD pre-capture là `NO_REFUND_REQUIRED`; online/provider và mọi case từ `PICKED_UP` trở đi fail-closed vào manual review. Delivered/defect claim chỉ là manual dispute trong cửa sổ 48 giờ. | Product | 2026-08-02 | T6 MVP policy |
| D2 | Approval authority | Chỉ deterministic pre-pickup mới có thể auto-request; post-pickup, customer dispute và mọi exception cần admin review. Admin mutation chưa mở trong T6. | Product/Ops | 2026-08-02 | T6 MVP policy |
| D3 | COD instrument | Trước khi thu tiền chỉ release reservation, không tạo customer refund. Sau settlement chỉ manual-approved ledger reverse, không tạo balance âm. | Product/Finance | 2026-08-02 | T6 MVP policy |
| D4 | Online provider/instrument | Refund về payment instrument gốc; wallet fallback chưa duyệt. Provider/API/callback/credential vẫn off và chuyển sang T7. | Product/Finance/Ops | 2026-08-02 | T7 gate remains |
| D5 | Component allocation | Dùng immutable Order snapshot; online captured pre-pickup dùng `totalPrice`; COD pre-capture refund amount bằng 0; voucher/flash-sale release/restore reservation, không biến discount thành tiền mặt. Post-pickup component split manual. | Finance | 2026-08-02 | T6 MVP policy |
| D6 | Refund state machine | `REQUESTED -> PROCESSING -> SUCCEEDED|PARTIAL|FAILED|MANUAL_REVIEW`; COD pre-capture dùng `NO_REFUND_REQUIRED`; Order/Delivery không tự đổi thành `REFUNDED`. | Architecture/Product | 2026-08-02 | T6 MVP policy |
| D7 | Identity/idempotency/event version | Stable `refundId`, event `eventId/eventType/occurredAt`, uniqueness `(orderId, trigger, component)`; exact replay trả case cũ, payload conflict fail-closed. | Architecture | 2026-08-02 | T6 MVP policy |
| D8 | Retry/DLQ/reconciliation | Chỉ retry lỗi hạ tầng tạm thời; provider unknown/amount mismatch/exhausted retry chuyển manual/DLQ. Outbox relay và reconciliation là gated, không retry mù provider. | Ops/Finance | 2026-08-02 | T7/provider follow-up |
| D9 | Audit/retention/security | Lưu actor/source/reason, snapshot tiền, before/after state, attempt/error, provider reference khi có; không lưu secret/card data/raw provider secret. | Security/Legal | 2026-08-02 | T6 MVP policy |
| D10 | Notification/UI | T6 chỉ mở admin read-only refund queue/projection; approve/reject/retry/reverse và customer money UI chưa mở. Customer status mapping chờ client phase sau. | Product | 2026-08-02 | T6 visibility |
| D11 | Rollout/rollback owner | DB → Settlement listener/case → read-only Gateway/admin visibility; mọi flag default-off. Rollback dừng trigger mới, giữ case/outbox để reconcile, không sửa ledger bất biến. | Operator | 2026-08-02 | T6 rollout |

## 8. Điều kiện mở T6/T7

### T6 — automatic refund implementation — trạng thái

- D1–D3, D5–D11 đã được điền theo bảng lựa chọn ở mục 7 và user đã duyệt policy
  MVP bảo thủ.
- Có canonical event schema và ownership cho refund policy.
- Có test data/fixture không dùng provider thật.
- Có rollback owner và manual reconciliation runbook.

T6 boundary đã triển khai state/idempotency, immutable cancellation/no-shipper
snapshot, reservation compensation event, source/reason eligibility, read-only
admin visibility và rollback/runbook; mọi flag mặc định `false`. Không tuyên bố
tiền đã được hoàn thật. Provider execution, admin mutation, ledger reverse và
customer money UI vẫn thuộc phase/gate khác.

### Chỉ mở T7 — online payment activation — khi

- D4 được duyệt.
- Provider, sandbox/production, callback origin và credential store đã được
  operator cung cấp ngoài repository.
- Signature, amount verification, status mapping, retry, reconciliation và
  rollback đã được duyệt.
- Có staging environment; không commit secret vào source.

## 9. Acceptance của packet này

- Policy MVP và authority cho T6 được ghi rõ ở mục 5–7; các ngoại lệ/provider T7
  vẫn là decision riêng.
- T6 code có thể thêm schema/event/read-only route nhưng không được bật provider,
  admin financial mutation hoặc client money flow mặc định.
- Không tuyên bố COD hoặc online provider refund đã hoạt động trong production.
