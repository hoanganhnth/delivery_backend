# Feature: Giao hàng & Ghép shipper (Delivery & Matching)

> Trạng thái tài liệu: 🟢 verified từ code; POD/exception là capability tắt mặc định
> Service liên quan: delivery-service (:8085), match-service (:8092), tracking-service (:8093), notification-service (:8091), routing-service (:8094)
> Repo: backend_delivery · Cập nhật: 2026-08-23

## 1. Mục đích
Sau khi có đơn, hệ thống tự tạo bản ghi giao hàng, tìm shipper phù hợp gần nhất
(retry liên tục nếu chưa có), cho shipper nhận/từ chối, rồi theo dõi tiến trình
giao đến khi hoàn tất và báo settlement.

## 2. Actor & quyền
| Actor | Được làm gì |
|---|---|
| SHIPPER | Recover offer/batch của chính mình, nhận/từ chối (`accept`), cập nhật vị trí/trạng thái delivery đã nhận, báo giao thất bại/retry và tạo POD khi capability được bật |
| ADMIN | Xem delivery và dùng status recovery có kiểm quyền; bulk-cancel thiếu audit/outbox đã xóa |
| SYSTEM | Saga/Match/Delivery giao tiếp qua Kafka và internal credential, không giả actor public |
| CUSTOMER | Xem tracking đơn của mình |

## 3. Điểm vào
| Loại | Định danh | Ghi chú |
|---|---|---|
| Kafka consume | `OrderCreatedEvent` | delivery tạo bản ghi (`createDeliveryFromOrderEvent`) |
| Kafka publish | `delivery.created.result` | Báo Saga tiếp tục luồng FIND_SHIPPER |
| Kafka consume | `FindShipperEvent` | match bắt đầu tìm shipper |
| Kafka publish | `shipper.found` | Saga nhận đúng một candidate rồi command Delivery persist offer |
| Kafka publish | `delivery.offer-persisted` | Saga chỉ cập nhật Order WAIT sau confirmation outbox này |
| Kafka publish | `delivery.shipper-offered` | Chỉ phát từ transactional outbox sau khi Delivery lưu offer/expiry |
| Kafka publish | `ShipperNotFoundEvent` | báo order + delivery khi hết retry |
| Kafka consume | `OrderCancelledEvent`, `STOP_MATCHING` | dừng tìm shipper |
| REST | `POST /api/deliveries/accept` | shipper ACCEPT/REJECT legacy single offer |
| REST | `POST /api/deliveries/batch/accept` / `/batch/reject` | shipper nhận hoặc từ chối nguyên batch, không partial accept |
| REST | `GET /api/deliveries/offers/current-batch` | self recovery batch offer với tối đa 3 delivery |
| REST | `GET /api/deliveries/batches/{batchId}` | snapshot recovery riêng của batch; kiểm owner và global stop sequence |
| REST | `GET /api/deliveries/offers/current` | self recovery đúng một offer chưa hết hạn sau startup/reconnect |
| REST | `POST /api/deliveries/cancel-assignment` | shipper hủy assignment trước pickup để rematch |
| REST | `PUT /api/deliveries/{id}/status` | self-assigned shipper đi tuần tự `PICKED_UP → DELIVERING → DELIVERED`; exact retry idempotent |
| REST | `POST /api/deliveries/{id}/proofs/upload-intent`, `.../{proofId}/confirm` | signed private POD upload, chỉ bật khi `DELIVERY_POD_ENABLED=true` |
| REST | `GET /api/deliveries/{id}/proofs/{proofId}/access` | signed private read cho participant/ADMIN, không lộ object key |
| REST | `POST /api/deliveries/{id}/exceptions/failed`, `/retry`, `/return/confirm` | shipper report/retry; restaurant owner xác nhận hoàn; chỉ bật khi `DELIVERY_EXCEPTION_ENABLED=true` |
| REST | `GET /api/deliveries/{id}/exception` | participant/ADMIN đọc trạng thái ngoại lệ đã tồn tại |

## 4. Trạng thái Delivery
`FINDING_SHIPPER → WAIT_SHIPPER_CONFIRM → ASSIGNED → PICKED_UP → DELIVERING → DELIVERED`, cùng nhánh
`CANCELLED` và `SHIPPER_NOT_FOUND`. Khi capability exception được bật, nhánh sau pickup là
`PICKED_UP|DELIVERING → RETURNING → RETURNED`; các trạng thái này không được
phát qua topic legacy `delivery.status-updated`.

```mermaid
stateDiagram-v2
    [*] --> FINDING_SHIPPER: OrderCreatedEvent
    FINDING_SHIPPER --> WAIT_SHIPPER_CONFIRM: persist one offer + expiry
    WAIT_SHIPPER_CONFIRM --> ASSIGNED: offered shipper ACCEPT
    WAIT_SHIPPER_CONFIRM --> FINDING_SHIPPER: REJECT / timeout / cancel assignment
    FINDING_SHIPPER --> SHIPPER_NOT_FOUND: hết retry
    FINDING_SHIPPER --> CANCELLED: order huỷ / STOP_MATCHING
    ASSIGNED --> PICKED_UP --> DELIVERING --> DELIVERED
    PICKED_UP --> RETURNING: failed delivery retry expires
    DELIVERING --> RETURNING: failed delivery retry expires
    RETURNING --> RETURNED: restaurant owner confirms return
    ASSIGNED --> FINDING_SHIPPER: shipper cancel-assignment trước pickup
    DELIVERED --> [*]
```

## 5. Luồng chính
**A. Tạo delivery** (`createDeliveryFromOrderEvent`): set pickup/delivery
toạ độ, `shippingFee` từ immutable Order snapshot, `totalPrice`,
`paymentMethod` (COD info), `restaurantId`, `creatorId`, legacy delivery estimate,
status
`FINDING_SHIPPER`, lưu → publish `delivery.created.result` cho Saga. Order là
owner của phí giao hàng và chỉ chấp nhận pickup/delivery coordinate đầy đủ,
hữu hạn, nằm trong Việt Nam. Thiếu hoặc sai fee/coordinate phải fail-closed;
Order và Delivery không được tự thay bằng minimum fee hoặc `15.000` mặc định.

**B. Tìm và giao offer**: Saga phát `saga.command.find-shipper`; Match đọc Redis
GEO replica quanh tọa độ pickup canonical của nhà hàng, lọc
exclusion/busy/offer reservation và kiểm COD eligibility qua
Settlement. Chỉ candidate gần nhất được reserve và publish `shipper.found`. Saga
command Delivery lưu đúng một `offeredShipperId + offerExpiresAt`, chuyển sang
`WAIT_SHIPPER_CONFIRM`, rồi outbox phát `delivery.offer-persisted` cho Saga và
`delivery.shipper-offered` cho Notification. Saga chỉ cập nhật Order sang
`WAIT_SHIPPER_CONFIRM` sau confirmation từ Delivery. Notification
lưu inbox đúng shipper và FCM chỉ là wake-up best-effort; source of truth để
startup/reconnect là self endpoint `/offers/current`.

Khi `BATCH_OFFER_ENABLED` được bật cho client, Match mở rolling round 5 giây
theo H3 cell/k-ring, có thể ghép tối đa 3 COD order có pickup gần nhau. Delivery
lưu `delivery_batches`/`delivery_batch_items`; shipper nhận toàn bộ batch. Một
item bị shipper huỷ trước pickup sẽ huỷ/rematch toàn batch để không làm sai
trạng thái BUSY của các item còn lại. Batch hoàn tất chỉ khi tất cả item
`DELIVERED` hoặc `RETURNED`; khi đó Match generation cũ được retire và Settlement đã consume
COD hold theo từng delivery. Route của batch là authority global stops
`0..(2*n-1)`: mỗi item có pickup và dropoff riêng, mọi stop phải xuất hiện đúng
một lần, và `pickupSequence < dropoffSequence`. Shipper recover snapshot qua
`GET /api/deliveries/batches/{batchId}`; snapshot cũ hoặc sai ownership bị từ chối.

Saga quét offer từ timeout tối thiểu được hỗ trợ nhưng chỉ rematch khi
deadline canonical `foundAt + waitingTimeoutSeconds` đã tới. Mỗi timeout command
mang đúng generation `deliveryId + shipperId + offerExpiresAt`; poll sớm là no-op,
payload không có duy nhất một `shipperId` dương thì fail-closed thay vì rematch.

Match fail-closed nếu command thiếu hoặc có pickup coordinate ngoài Việt Nam;
không được fallback sang địa chỉ giao hoặc một tọa độ trung tâm mặc định vì có
thể offer shipper sai khu vực.

Mỗi initial find/rematch mang `matchingSessionId` do Saga sở hữu và lưu trong
step `MATCHING_STARTED`. Match lưu durable command/tombstone theo
`(deliveryId, matchingSessionId)`: stop cùng generation đến trước find làm command
sau đó `CANCELLED` mà không chạy GEO; stop generation cũ không được xóa offer của
rematch mới. `shipper.found` và `shipper.not-found` trả lại cùng generation, nên
Saga bỏ qua kết quả cũ khi đã rematch.

### B1. Giải thích quyết định và Scenario Lab

Match hiện vẫn dùng thuật toán active `nearest-cod-v1`: lấy candidate theo thứ
tự gần nhất từ Redis GEO replica, sau đó kiểm COD tuần tự và chỉ reserve một
shipper. Sau khi kết quả nghiệp vụ đã được ghi bền, Match ghi thêm event
read-only `matching.decision-trace` qua outbox. Trace có `eventVersion`,
algorithm/version, số lần query, latency tổng và latency từng stage
`GEO_QUERY`, `COD_ELIGIBILITY`, `RESERVE`, `OUTCOME`, cùng candidate/rank,
khoảng cách, COD eligibility và lý do bị loại.

`simulator-service` consume topic này bằng consumer group riêng
`simulator-algorithm-observer`, ghép theo cả `orderId` và `deliveryId`, rồi
đẩy vào panel Decision Trace của `delivery_simulator_web` qua SSE/REST. Trace
không được dùng để quyết định offer, không thay thế candidate oracle của
scenario và mất trace cũng không làm fail việc phân đơn. Nếu Kafka trace đến
trước lúc runner kịp poll được ID đơn/delivery, simulator giữ tạm trong bộ nhớ
20 phút rồi ghép lại ở snapshot kế tiếp.

Đây là nền tảng để so sánh thuật toán sau này; chưa có algorithm mới chạy
`SHADOW`, chưa có baseline-vs-candidate score và chưa cho algorithm thử nghiệm
reserve/send offer.

**C. Shipper nhận** (`acceptDelivery`, action=ACCEPT): chỉ shipper được offer và
offer chưa hết hạn mới được set shipperId, status
`ASSIGNED`, publish shipper "BUSY", `MatchAcceptedEvent`, WebSocket update.

**D. Tiến trình giao**: shipper cập nhật status `PICKED_UP → DELIVERING →
DELIVERED`; mỗi bước publish `delivery.status-updated`. Client recover trạng
thái qua REST detail/inbox notification; raw WebSocket chỉ thuộc location
tracking. Khi DELIVERED → publish `DeliveryCompletedEvent` cho settlement.

**D1. POD, giao thất bại và hoàn hàng (gated)**: khi `DELIVERY_POD_ENABLED=true`,
shipper phải tạo signed upload intent, upload ảnh JPEG/PNG/WebP tối đa 10 MB và
xác nhận metadata với provider private trước `DELIVERED`; proof chỉ đọc qua
signed URL và purge sau 90 ngày. Khi `DELIVERY_EXCEPTION_ENABLED=true`, shipper
có thể report failure chỉ sau pickup. Case có đúng một retry trong 15 phút; hết
hạn hoặc report lại sau retry chuyển delivery sang `RETURNING`, không phát status
legacy. Restaurant owner của delivery xác nhận `RETURNED`. Event outbox riêng
`delivery.exception.reported` giữ immutable delivery money snapshot theo công
thức `subtotal + grossShippingFee - totalPrice`; Settlement chỉ tạo case
`MANUAL_REVIEW`, không gọi provider/refund executor. Mọi flag này mặc định false;
registry object storage fail-closed cho tới khi có private adapter rõ ràng.

**E. Saga ordering, timeout và compensation**: Saga claim `eventId` trong
`saga_inbound_receipts` trước khi đổi state hoặc ghi command outbox. Nếu
`order.cancelled` hoặc `restaurant.order-confirmed` đến trước khi Saga được tạo,
fact hợp lệ được lưu bền trong `saga_early_events`; khi `order.created` tới,
Saga drain các fact trước khi phát `create-delivery`. `SagaEarlyEventScheduler`
quét lại cửa sổ race nếu fact commit ngay sau lần drain đầu tiên. Phạm vi staging
hiện chỉ gồm hai topic này; topic khác vẫn phải chờ Saga và đi theo retry/DLT.

Timeout scheduler không phát JSON vô danh. Nó tạo command nội bộ có `eventId`
deterministic, `expectedStatus`, `expectedVersion`, `observedUpdatedAt` và
`deadlineAt`; Saga lock lại aggregate, rồi stale/duplicate/đến sớm chỉ là no-op.
Offer timeout chỉ được xử lý sau `foundAt + waitingTimeoutSeconds`.

Lệnh match đầu tiên mang `matchingDeadlineAt` tuyệt đối do Saga sở hữu; mọi
rematch giữ nguyên deadline. Match kiểm tra cutoff trước khi tìm, retry, reserve
và publish. Nếu cutoff đã qua, reservation được release và phát một
`shipper.not-found` deterministic.

Khi đã biết Delivery, cancellation giữ Saga ở `COMPENSATING` cho tới khi
`delivery.status-updated(CANCELLED)` được phát atomically cùng state Delivery.
`delivery.cancel.failed` không bị bỏ qua: Saga ghi `DELIVERY_CANCEL_FAILED`,
chuyển `FAILED` và tạo dấu hiệu cho manual reconciliation. Policy public khi
cancellation chạy sau pickup và refund vẫn là quyết định product riêng.

## 6. Các case & nhánh rẽ (verify)
| # | Điều kiện | Hành vi | Kết quả |
|---|---|---|---|
| C1 | Create-delivery command replay exact | so identity và trả record cũ, không phát event lần hai | Idempotent |
| C2 | Accept nhưng role ≠ SHIPPER | chặn | AccessDenied |
| C3 | action ∉ {ACCEPT, REJECT} | chặn | `InvalidStatusException` |
| C4 | REJECT không có `rejectReason` | chặn | `InvalidStatusException` |
| C5 | **Shipper đã có đơn active mà nhận đơn mới** | `findActiveDeliveriesByShipper` không rỗng → chặn | Bắt hoàn thành đơn hiện tại trước |
| C6 | Accept khi delivery status ≠ WAIT_SHIPPER_CONFIRM hoặc offer hết hạn | chặn | `InvalidStatusException` |
| C7 | Delivery đã gán shipper khác | chặn | `InvalidStatusException("đã giao cho shipper khác")` |
| C8 | Shipper REJECT | reset shipperId=null, status=FINDING_SHIPPER, lưu rejectReason → publish rejected; exact retry trước rematch không publish lần hai | Đơn quay lại tìm shipper mới |
| C9 | Tìm shipper: kết quả rỗng | `Mono.error("No shippers found")` → retry backoff | Thử lại trong SLA của Saga |
| C10 | Tất cả shipper bị loại bởi exclusion list | `Mono.error(...all filtered)` → retry | Như C9, không vượt `matchingDeadlineAt` |
| C11 | Delivery bị hủy giữa lúc match của một generation | durable stop fence + `isCancelled(deliveryId, matchingSessionId)` dừng chain, không publish found | Ngừng đúng match attempt an toàn |
| C12 | Recover offer bằng role khác SHIPPER hoặc nhiều offer active | chặn/fail-closed | AccessDenied / invariant error |
| C13 | Acceptance cũ từ shipper đã reject/hủy assignment đến sau khi Saga rematch | bỏ qua theo rejection history | Không resurrect assignment cũ |
| C14 | Acceptance hợp lệ commit sát deadline đến sau Saga timeout | cho hội tụ `SHIPPER_ASSIGNED`; exact-expire stale là no-op ở Delivery | Giữ assignment đã commit |
| C15 | Hết retry/candidate hoặc qua `matchingDeadlineAt` | Saga phát riêng `mark-shipper-not-found` cho Delivery và status command cho Order | Cả hai cùng về `SHIPPER_NOT_FOUND`, không bị nhầm thành cancellation |
| C16 | `order.cancelled` đến trước khi Saga tồn tại | Lưu fact vào `saga_early_events`, promote khi `order.created` tới | Không phát `create-delivery` cho đơn đã huỷ |
| C17 | `restaurant.order-confirmed` đến trước `order.created` | Staging rồi drain trước create; nếu Delivery đã tồn tại thì mở cổng match | Không mất quyết định nhà hàng, không match trước khi Delivery sẵn sàng |
| C18 | Timeout duplicate/stale/đến sớm | Re-lock + fence status/version/timestamp/deadline; claim inbox chỉ sau khi due | No-op, không compensation trùng hoặc ghi DLT giả |
| C19 | `delivery.created.result` đến sau Saga `CANCELLED`/`FAILED` | Ghi identity Delivery và phát lại `cancel-delivery` | Không để Delivery mồ côi |
| C20 | Đã gửi cancel nhưng Delivery từ chối (`delivery.cancel.failed`) | Ghi step lỗi, chuyển Saga `FAILED`, giữ dấu manual reconciliation | Không che giấu drift giữa Order/Delivery |
| C21 | Cancellation có Delivery đã biết | Chờ `delivery.status-updated(CANCELLED)` để kết thúc compensation | Saga hội tụ `COMPENSATING → CANCELLED` |
| C22 | `stop-matching` đến trước `find-shipper` trên topic khác | Match ghi tombstone theo session; find cùng session persist `CANCELLED`, không query GEO | Không resurrect matching đã dừng |
| C23 | `shipper.found`/`shipper.not-found` generation cũ đến sau rematch | Saga so `matchingSessionId` với `MATCHING_STARTED` hiện hành và bỏ qua mismatch | Result cũ không thắng rematch mới |
| C24 | POD bật nhưng chưa có proof `CONFIRMED` | chặn transition `DELIVERED` | Không phát completion/ledger event |
| C25 | POD upload metadata sai type/size hoặc storage chưa cấu hình | reject trước/sau signed upload theo đúng boundary | Không lưu proof confirmed; storage thiếu trả 503 |
| C26 | Failure sau pickup lần đầu | persist case + deadline 15 phút, publish dedicated event | `RETRY_AVAILABLE`; Settlement review case chỉ khi consumer flag bật |
| C27 | Retry hết hạn hoặc đã dùng retry mà report lại | lock delivery/case và chuyển `RETURNING` | Không phát `RETURNING` qua legacy topic |
| C28 | Restaurant owner khác hoặc case chưa `RETURNING` xác nhận hoàn | chặn ownership/state | Không chuyển `RETURNED`/release shipper |

## 7. Quy tắc nghiệp vụ
- **Một shipper chỉ 1 đơn active tại một thời điểm** (C5) — guard ở accept.
- **Không match lại shipper đã từ chối** đơn đó (exclusion list).
- REJECT không phạt, chỉ đưa đơn về hàng đợi tìm shipper.
- Match nhận command Kafka và tính khoảng cách từ Redis GEO replica của Tracking.
- Notification STOMP đã bị xóa; offer dùng durable inbox, FCM wake-up và self
  recovery REST, không dùng WebSocket notification.
- POD/exception flags chỉ được bật cùng private storage adapter, Kafka topic/DLT,
  Settlement review bridge và client UX đã rehearsal; flags mặc định false.

## 8. Case lỗi & ngoại lệ
| Tình huống | Xử lý | Exception |
|---|---|---|
| Không tìm thấy delivery theo orderId | `findByOrderId` | `ResourceNotFoundException` |
| Trạng thái không hợp lệ | các guard | `InvalidStatusException` |
| Thiếu/sai eventId hoặc identity | reject ở Kafka boundary, retry/DLT theo loại lỗi | `IllegalArgumentException` |
| Lỗi hạ tầng khi tạo delivery/outbox | giữ exception để retry/DLT | `RuntimeException` |
| Fact cancellation/confirmation đến trước Saga | durable staging rồi replay | không DLT vì thiếu aggregate |
| Timeout stale/đến sớm | no-op sau fence/deadline check | không tạo side effect |
| Delivery từ chối cancellation | phát `delivery.cancel.failed`, Saga `FAILED` | manual reconciliation |

## 9. Phụ thuộc
tracking-service (Redis GEO/location event), notification-service (durable inbox + FCM),
order-service (status sync), settlement-service (DeliveryCompleted), Saga
(config retry + orchestration), Kafka.

## 10. Khoảng trống / rủi ro đã biết
- ✅ **Shipper hủy SAU khi accept** — ĐÃ LÀM (2026-07-22): endpoint
  `POST /api/deliveries/cancel-assignment` reset đơn về FINDING_SHIPPER, giải phóng
  shipper, re-trigger rematch (loại trừ shipper vừa huỷ) qua cơ chế Saga. Chỉ cho
  huỷ khi status ASSIGNED (chưa lấy hàng). Xem [learning/003](../../learning/003-shipper-cancel-after-accept.md).
- ⚠️ Lưu ý: `DeliveryWaitingService` mà review cũ nhắc **không tồn tại** trong code;
  retry là do match-service (reactive backoff) + Saga điều phối rematch.
- **Match ack chậm**: chain reactive `.subscribe()` ack Kafka *sau khi* xong retry
  (có thể rất lâu) → rủi ro giữ offset/rebalance. (Lưu ý: **không** block thread
  bằng `Thread.sleep` như review cũ ghi — là reactive; nhưng độ trễ ack vẫn cần xử lý.)
- Early-event staging hiện chỉ bao phủ `order.cancelled` và
  `restaurant.order-confirmed`; retention/cleanup, metrics và mở rộng reducer
  cho các topic khác cần một phase có authority riêng.
- Match dùng `match_db` cho command receipt/fingerprint, candidate staging,
  generation cancellation tombstone và result outbox; Redis vẫn chỉ là GEO/offer
  projection rebuildable. H2/Flyway và full Match suite đã chứng minh replay,
  stop-before-find và stale-generation logic; runtime replay sau mất
  PostgreSQL/Kafka/Redis vẫn chưa được chứng minh.
- Cancellation-vs-pickup winner, refund và manual recovery UX sau pickup chưa
  được chọn bởi product owner; code hiện tạo review-only exception case và để lại
  dấu audit, còn provider/refund decision vẫn thuộc Settlement manual review.
- POD private provider, Kafka/PostgreSQL runtime replay, scheduler recovery và
  device upload/restaurant-confirm UX chưa có runtime proof; không bật flag dựa
  trên unit/H2 proof.
- Runtime PostgreSQL/Kafka/Redis proof cho offer expiry, concurrent accept,
  reconnect và replay vẫn OPEN tại Gate B8.

## 11. Câu hỏi mở cho review
- Khi shipper đã ACCEPT rồi gặp sự cố, quy trình mong muốn là gì (tự rematch? phạt?)?
- `maxRetryAttempts`/delay hiện lấy từ Saga — giá trị nghiệp vụ mong muốn là bao nhiêu?
- Chốt disconnect grace/multi-session lease cho location availability trước khi
  auto-offline khi raw WebSocket đóng.
