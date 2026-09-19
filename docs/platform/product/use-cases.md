# System Use-Case Inventory — Delivery Platform

> Cập nhật: 2026-09-14. Đây là inventory source-derived cho toàn bộ platform,
> được đưa vào public read-only Docs Portal tại /system-overview/docs của
> delivery_web.
>
> “Use case” ở đây là một mục tiêu nghiệp vụ của actor hoặc một trách nhiệm
> runtime của platform. Một endpoint, một nút UI, một retry hoặc một event
> không mặc nhiên là một use case riêng; mapping chi tiết từng HTTP operation
> vẫn nằm trong [HTTP contract catalog](../system/api/http-contract-catalog.md).

## 1. Phạm vi và nguồn sự thật

Inventory này được đối chiếu từ:

- [Platform product overview](./overview.md), [service catalog](../system/service-catalog.md)
  và [domain workflows](../system/workflows.md).
- [Client boundary](../system/clients.md), [delivery_web action-contract matrix](../../../../delivery_web/docs/action-contract-matrix.md),
  [delivery_web feature catalog](../../../../delivery_web/APP_FEATURES.md),
  [customer app README](../../../../delivery_app/README.md) và
  [shipper app README](../../../../shipper_app2/README.md).
- [HTTP contract catalog](../system/api/http-contract-catalog.md), các feature
  docs về [order lifecycle](./features/order-lifecycle.md),
  [delivery and matching](./features/delivery-matching.md),
  [settlement](./features/settlement.md),
  [serviceability and ETA](./features/serviceability-and-eta.md),
  [menu inventory](./features/menu-inventory.md) và
  [voucher/flash-sale checkout](./features/voucher-flashsale-checkout.md).

backend_delivery/USE_CASES.md là snapshot legacy dùng để tìm manh mối, không
phải authority hiện hành. Khi snapshot đó mâu thuẫn với code, Gateway allow-list,
client action matrix hoặc platform docs, inventory này theo nguồn hiện hành.

### Status vocabulary

| Status | Ý nghĩa |
| --- | --- |
| active | Đường đi được hỗ trợ trong boundary hiện tại; có thể vẫn yêu cầu role/ownership. |
| gated | Có code/contract hoặc một phần UI, nhưng feature flag, optional runtime, provider hoặc rollout gate đang tắt. |
| experimental | Có boundary/projection/provider integration nhưng chưa là public MVP contract. |
| internal | Chỉ service-to-service hoặc control-plane; không phải thao tác của client. |
| dev-only | Chỉ phục vụ simulator, scenario lab hoặc tooling phát triển. |
| not exposed | Có backend/source surface nhưng chưa có client route được hỗ trợ; không được suy diễn là public feature. |
| decision-required | Cần product/owner policy hoặc provider/reconciliation decision trước khi coi là capability. |

protected trong các bảng là access boundary, không phải một status riêng: resource
service vẫn phải kiểm tra JWT role và ownership.

## 2. Actors và bề mặt sản phẩm

| Actor | Bề mặt | Quyền hạn chính |
| --- | --- | --- |
| Anonymous | Catalog công khai, email verification, public handbook | Đọc catalog/menu được publish; không tạo order nếu chưa có USER. |
| USER / Customer | delivery_app, Customer Web | Đăng nhập, catalog, cart, checkout COD, order, tracking, profile/address và notification. |
| SHOP_OWNER / Restaurant owner | Restaurant Web | Quản lý restaurant/menu/catalog import, quyết định order, rating read và các campaign surface khi được mở. |
| SHIPPER | shipper_app2 | Nhận offer, cập nhật lifecycle giao, publish location, đọc lịch sử/profile và notification. |
| ADMIN | Admin Web và các admin API | Quan sát/moderation catalog, order, shipper, rating, campaign, chat và analytics theo boundary hiện hành. |
| Platform / service | Kafka, Redis, PostgreSQL, Gateway và các service private | Orchestrate workflow, authorize resource, reserve/compensate, settle và recover. |
| Operator / developer | Operations docs và simulator | Kiểm tra runtime, reconciliation, scenario lab và recovery; không phải business actor public. |

## 3. Anonymous và Customer (USER)

| ID | Use case | Access / surface | Status | Contract và evidence chính |
| --- | --- | --- | --- | --- |
| C-01 | Duyệt danh sách nhà hàng | Anonymous/USER; Customer Web, Flutter | active | GET /api/restaurants; [Web action matrix](../../../../delivery_web/docs/action-contract-matrix.md), restaurant-service |
| C-02 | Tìm kiếm nhà hàng | Anonymous/USER; Customer Web, Flutter | active | GET /api/restaurants/search; kết quả rỗng khác lỗi request |
| C-03 | Xem chi tiết nhà hàng và menu đang bán | Anonymous/USER; Customer Web, Flutter | active | GET /api/restaurants/{id} và GET /api/menu-items/restaurant/{restaurantId}/available; server là catalog authority |
| C-04 | Đăng ký tài khoản customer | Anonymous → USER; Customer Web, Flutter | active | Auth registration rồi User profile handoff; provisioning token opaque, retry theo authId; [registration workflow](../system/workflows.md) |
| C-05 | Xác minh email và khôi phục mật khẩu | Anonymous/USER; verification/recovery pages và Auth API | active | Email verification, forgot/reset password; token hết hạn hoặc sai phải fail-closed |
| C-06 | Đăng nhập customer | USER; Customer Web, Flutter | active | POST /api/auth/login với role USER; sai role không được mở customer session |
| C-07 | Social/biometric login boundary | USER; provider/device capability | gated | Auth/client adapters có boundary; provider/native availability phải được cấu hình, không thay thế password contract |
| C-08 | Refresh và logout session | USER; Customer Web, Flutter | active | POST /api/auth/refresh-token, POST /api/auth/logout; refresh single-flight, logout luôn clear local session |
| C-09 | Xem/cập nhật profile | USER; Customer Web, Flutter | active | GET/PUT /api/users; profile identity do JWT/Auth link quyết định |
| C-10 | Quản lý sổ địa chỉ giao hàng | USER; Customer Web, Flutter | active | List/create/update/delete/set-default dưới /api/addresses; tọa độ là input canonical cho checkout |
| C-11 | Quản lý cart local một nhà hàng | Anonymous/USER; Customer Web, Flutter | active | Local cart, quantity bounds, note và explicit restaurant switch; không phải database/cart API |
| C-12 | Xem checkout preview và quote | USER; Customer Web, Flutter | active | POST /api/orders/checkout-preview; server tính giá/phí/discount, quote có TTL |
| C-13 | Tạo đơn COD idempotent | USER; Customer Web, Flutter | active | POST /api/orders với quoteId và UUID Idempotency-Key; immutable money/location snapshot |
| C-14 | Xem lịch sử đơn | USER; Customer Web, Flutter | active | GET /api/orders/my-orders?page=&size=; pagination và error/retry là trạng thái thật |
| C-15 | Xem chi tiết và refresh trạng thái đơn | USER; Customer Web, Flutter | active | GET /api/orders/{id}; backend kiểm participant ownership, client refresh/poll khi order còn active |
| C-16 | Hủy đơn trước pickup | USER; Customer Web, Flutter | active | PUT /api/orders/{id}/cancel; UI chỉ mở ở trạng thái cho phép, backend là authority cuối |
| C-17 | Theo dõi vị trí shipper realtime | USER; Flutter hiện tại; Web browser chưa mở | active | Authorized raw WebSocket delivery room; location không phải lịch sử relational mỗi ping |
| C-18 | Đọc notification inbox và wake-up | USER; Flutter | active | Durable notification inbox; FCM chỉ đánh thức app để đọc lại Gateway, không phải business truth |
| C-19 | Gửi rating sau order đủ điều kiện | USER; client surface tùy rollout | active | POST /api/restaurants/{restaurantId}/ratings, Order Service kiểm eligibility; rating status có moderation |
| C-20 | Xem trạng thái refund/case của chính mình | USER; Flutter read-only surface | active | GET /api/settlement/refunds/my; chỉ là status-safe read, không phải refund mutation hay provider guarantee |
| C-21 | Thanh toán online/provider payment | USER; client/provider | decision-required | Payment/callback/provider contract có source nhưng online payment chưa là public MVP; Web UI bị loại khỏi boundary |
| C-22 | Thu thập và áp dụng voucher | USER; Flutter/Web khi gate mở | gated | Voucher wallet/calculate/reserve/release tồn tại; checkout relay và client activation mặc định off |
| C-23 | Xem và mua flash-sale item | USER; public campaign/client | gated | Public campaign/read và stock reservation có contract; order reservation/checkout activation chưa được coi là active |
| C-24 | Xem livestream, join viewer và xem pinned product | USER; Flutter/Web surface thử nghiệm | experimental | Livestream metadata/token/Agora boundary tồn tại nhưng Gateway/public MVP/provider E2E chưa mở |
| C-25 | Chat customer với CSKH | USER ↔ ADMIN; Firebase | gated | Custom-token/Firestore Rules/indexes phải được provision; fail-closed khi dependency ngoài thiếu |
| C-26 | Reorder, browser realtime map, browser notification/rating | Customer Web | not exposed | Explicit Web exclusions; không tạo endpoint giả từ preview hoặc component lịch sử |

## 4. Restaurant owner (SHOP_OWNER)

| ID | Use case | Access / surface | Status | Contract và evidence chính |
| --- | --- | --- | --- | --- |
| O-01 | Đăng nhập, restore và logout portal nhà hàng | SHOP_OWNER; Restaurant Web | active | Auth role/ownership; session refresh single-flight; portal chỉ mở sau role check |
| O-02 | Xem và quản lý restaurant profile | SHOP_OWNER; Restaurant Web | active | GET /api/restaurants/my-restaurants, POST/PUT /api/restaurants; server kiểm owner |
| O-03 | Quản lý menu item | SHOP_OWNER; Restaurant Web | active | GET /api/menu-items/my-menu-items, POST/PUT/DELETE /api/menu-items; không optimistic success khi API lỗi |
| O-04 | Import restaurant/menu catalog tuần tự | SHOP_OWNER; Restaurant Web | active | Preview/validation rồi create/update restaurant trước menu; lỗi theo record, không giả transaction toàn file |
| O-05 | Xem order của restaurant | SHOP_OWNER; Restaurant Web | active | GET /api/orders/my-restaurant-orders với pagination/filter trạng thái |
| O-06 | Confirm hoặc reject order pending | SHOP_OWNER; Restaurant Web | active | POST /api/restaurants/orders/{orderId}/confirm hoặc /reject; reject cần reason, outbox decision atomic |
| O-07 | Xem rating đã được duyệt | SHOP_OWNER; Restaurant Web | active | GET /api/restaurants/{restaurantId}/ratings; chỉ dữ liệu theo restaurant ownership/status |
| O-08 | Đọc và gửi shop voucher chờ duyệt | SHOP_OWNER; Restaurant Web | gated | GET/POST /api/promotions/shop; service/optional-capability và checkout rollout vẫn off |
| O-09 | Quản lý serviceability zones/polygon và ETA | SHOP_OWNER; owner API surface | gated | /api/restaurants/{restaurantId}/serviceability-zones, routing/provider boundary; [feature doc](./features/serviceability-and-eta.md) |
| O-10 | Quản lý menu inventory | SHOP_OWNER; owner API surface | gated | /api/menu-items/{menuItemId}/inventory; reservation/commit/release phải cùng rollout order |
| O-11 | Đăng ký món vào flash-sale campaign | SHOP_OWNER; merchant API | gated | POST /api/flashsales/merchant/items; backend contract có nhưng current Web MVP chưa expose campaign registration đầy đủ |
| O-12 | Host livestream và quản lý pinned product | SHOP_OWNER; Web/Agora | experimental | Livestream create/start/end/token/product routes; provider, moderation và Gateway gate chưa public |
| O-13 | Xem analytics/revenue dashboard nhà hàng | SHOP_OWNER; analytics surface | experimental | GET /api/analytics/dashboard/my-restaurant hoặc restaurant scope; projection/backfill ownership chưa mở |
| O-14 | Xem balance, earnings, transaction hoặc request withdrawal | SHOP_OWNER; settlement API | not exposed | Contract/code surface có thể tồn tại, nhưng Web MVP explicit-excludes settlement/withdrawal UI |
| O-15 | Owner chat/media upload/typing presence | SHOP_OWNER; Web | not exposed | Không có current supported route; không suy diễn từ Firebase/chat/livestream preview |

## 5. Shipper (SHIPPER)

| ID | Use case | Access / surface | Status | Contract và evidence chính |
| --- | --- | --- | --- | --- |
| SHP-01 | Đăng nhập password hoặc Google provider | SHIPPER; React Native | active | POST /api/auth/login hoặc POST /api/auth/social-login với role SHIPPER; provider cần cấu hình |
| SHP-02 | Refresh, revoke và restore session/device identity | SHIPPER; React Native | active | Single-flight refresh, persisted device identity, logout clear local state kể cả remote revoke lỗi |
| SHP-03 | Xem/cập nhật profile, vehicle và documents | SHIPPER; React Native | active | GET/PUT /api/shippers/my-profile và các profile/document client surfaces |
| SHP-04 | Chuyển online/offline availability | SHIPPER; React Native | active | PATCH /api/shippers/online-status; availability là input cho matching, không phải client tự chọn offer |
| SHP-05 | Khôi phục current offer sau push/process restart | SHIPPER; React Native | active | GET /api/deliveries/offers/current; FCM không là nguồn sự thật |
| SHP-06 | Accept hoặc reject single delivery offer | SHIPPER; React Native | active | POST /api/deliveries/accept; kiểm offer owner, expiry, active-delivery guard |
| SHP-07 | Accept hoặc reject batch offer | SHIPPER; React Native | gated | /api/deliveries/batch/accept hoặc /reject, batch snapshot/route policy; canary flag chưa là default MVP |
| SHP-08 | Hủy assignment trước pickup để rematch | SHIPPER; React Native | active | POST /api/deliveries/cancel-assignment; exact reason/state fence và matching session bảo vệ replay |
| SHP-09 | Cập nhật lifecycle giao hàng | SHIPPER; React Native | active | PUT /api/deliveries/{id}/status: ASSIGNED → PICKED_UP → DELIVERING → DELIVERED |
| SHP-10 | Nhận route/map và publish GPS location | SHIPPER; React Native | active | Mapbox adapter, native GPS và raw Gateway WebSocket; tracking kiểm generation/lease |
| SHP-11 | Xem active delivery, history, detail và success | SHIPPER; React Native | active | Self-scoped delivery read; recover được sau app kill/reconnect |
| SHP-12 | Proof of delivery, báo failed delivery, retry và return | SHIPPER/SHOP_OWNER; delivery surface | gated | Signed POD và exception routes chỉ mở khi DELIVERY_POD_ENABLED/DELIVERY_EXCEPTION_ENABLED cùng proof |
| SHP-13 | Đọc notification inbox và nhận wake-up offer | SHIPPER; React Native | active | Durable inbox + FCM wake-up; accept chỉ sau current offer read từ backend |
| SHP-14 | Xem rating của chính mình | SHIPPER; React Native | active | GET /api/shippers/me/ratings; read-only self scope |
| SHP-15 | Xem earnings/balance, deposit, transaction và withdrawal | SHIPPER; source/API surface | not exposed | Settlement self-service mutation, payout, tip, incentive và tier đang hidden/default-off trong client policy |
| SHP-16 | Shipper self-registration hoặc forgot/change password riêng | SHIPPER; React Native | not exposed | Auth → Shipper onboarding atomic/recoverable chưa được owner-approved; xem [auth contract](../../../../shipper_app2/docs/AUTH_CONTRACT.md) |

## 6. Admin (ADMIN)

| ID | Use case | Access / surface | Status | Contract và evidence chính |
| --- | --- | --- | --- | --- |
| ADM-01 | Đăng nhập và quản lý session admin | ADMIN; Admin Web | active | Admin login role-bound, profile bootstrap, refresh và logout |
| ADM-02 | Xem dashboard platform | ADMIN; /admin/dashboard | experimental | Analytics dashboard route/UI tồn tại; analytics projection/backfill và runtime enablement vẫn là experimental |
| ADM-03 | Xem, phân trang và lọc toàn bộ order | ADMIN; /admin/orders | active | GET /api/orders/all, GET /api/orders/status/{status}; page/size bounded |
| ADM-04 | Xem/tìm restaurant và mở owner portal | ADMIN; /admin/restaurants | active | Admin read/search và navigation; mutation vẫn tuân ownership boundary |
| ADM-05 | Import restaurant/menu catalog | ADMIN; /admin/catalog-import | active | Cùng sequential preview/validation contract với owner import |
| ADM-06 | Xem shipper và filter online/offline | ADMIN; /admin/shippers | active | GET /api/shippers, /api/shippers/online; offline tab là local filter, không ngầm có block/delete |
| ADM-07 | Moderation rating approve/reject | ADMIN; /admin/ratings | active | GET /api/restaurants/admin/ratings, PUT .../{id}/status; failed mutation phải retry được |
| ADM-08 | Tạo/xóa platform coupon | ADMIN; /admin/coupons | gated | POST /api/promotions/platform, DELETE /api/promotions/{id}; promotion service/checkout gate còn off |
| ADM-09 | Duyệt/từ chối shop voucher | ADMIN; /admin/coupons | gated | Pending-shop list và approve/reject routes; không đồng nghĩa voucher đã dùng được ở checkout |
| ADM-10 | Quản lý flash-sale campaign và approve item | ADMIN; /admin/flash-sales | gated | Campaign create/status/items/approve routes; optional service/runtime gate phải mở riêng |
| ADM-11 | Support inbox, reply text, mark read và close conversation | ADMIN; /admin/chat | gated | Firebase custom token + Firestore Rules/indexes; fail-closed nếu external setup thiếu |
| ADM-12 | Quản lý user/account, thống kê, block/unblock | ADMIN; admin API, chưa có Web MVP route | not exposed | /api/users/admin/* và Auth account block surface có contract; current Web exclusions không cho phép suy diễn CRUD UI |
| ADM-13 | Đọc settlement balances, transactions, revenue và refund cases | ADMIN; settlement API, chưa có Web MVP route | not exposed | Admin settlement reads tồn tại; Web explicit-excludes settlement/withdrawal/refund UI |
| ADM-14 | Livestream admin studio/moderation | ADMIN; source/preview surface | experimental | Livestream lifecycle/token/product/moderation boundary chưa public MVP/provider-proven |
| ADM-15 | Manual assign, browser shipper map, cancel/refund mutation | ADMIN; legacy/preview ideas | not exposed | Không có supported current Web action contract; không biến route/controller cũ thành quyền mới |

## 7. Platform và system automation

Các mục dưới đây không có actor public trực tiếp. Chúng là các use case runtime
phải hoàn tất để các use case client ở trên có hành vi đúng.

| ID | Use case hệ thống | Service/boundary | Status | Kết quả/điểm kiểm soát |
| --- | --- | --- | --- | --- |
| SYS-01 | Auth identity → User profile handoff | Auth, User | internal | Hai request độc lập, opaque provisioning token, retry/resume theo authId, không tạo profile trùng |
| SYS-02 | Gateway forward và resource authorization | Gateway, resource services, JWKS | internal | Gateway rate-limit/CORS/strip legacy headers; resource service tự verify issuer/audience/kid/token type/role/ownership |
| SYS-03 | Refresh rotation sau 401 | Client session adapters, Auth | internal | Một refresh in-flight, lưu cả token pair rồi retry original request tối đa một lần |
| SYS-04 | Canonical quote và create-order retry boundary | Order, Restaurant | internal | Quote TTL, re-price, price/availability validation, idempotency receipt và immutable snapshot |
| SYS-05 | Reservation/compensation trong create order | Order, Promotion, Flash Sale, Inventory | gated | Reserve rồi commit/release bằng stable reservation ID; timeout không được giả thành success |
| SYS-06 | Restaurant decision orchestration | Restaurant, Order, Kafka, Saga | internal | Confirm/reject và outbox commit atomic; early event được stage/replay sau order.created |
| SYS-07 | Tạo delivery và bắt đầu matching | Saga, Delivery, Match | internal | order.created → delivery.created.result → find-shipper command; không đọc chéo database |
| SYS-08 | Chọn shipper eligible | Match, Redis GEO, Settlement | internal | Nearest COD candidate, busy/exclusion/lease/COD eligibility fence, một reservation tại một thời điểm |
| SYS-09 | Offer expiry, reject và rematch | Saga, Match, Delivery | internal | Deadline tuyệt đối, generation/session fence, stale/duplicate timeout là no-op, hết retry giữ SHIPPER_NOT_FOUND |
| SYS-10 | Delivery lifecycle và terminal event | Delivery, Kafka | internal | ASSIGNED → PICKED_UP → DELIVERING → DELIVERED; delivery.completed là settlement trigger duy nhất |
| SYS-11 | Batch delivery offer | Match, Delivery, Shipper client | gated | Tối đa 3 item, global stop sequence, all-item completion và batch cancellation/rematch; chỉ canary |
| SYS-12 | Authorized realtime location | Tracking, Redis, Delivery, Gateway WebSocket | internal | Shipper publish theo lease/generation; viewer chỉ join đúng delivery room; history chỉ sampled support data |
| SYS-13 | Durable notification và FCM wake-up | Notification, Kafka, Firebase | internal | Inbox commit trước wake-up; mất FCM không làm mất offer/business event |
| SYS-14 | COD settlement ledger | Settlement, PostgreSQL, Kafka | internal | Validate identity/money conservation, receipt + immutable ledger + balance projection một transaction |
| SYS-15 | Settlement replay/reconciliation | Settlement, DLT/operator | internal | Exact duplicate no-op; conflict/amount mismatch fail-closed, retry/DLT/manual reconciliation |
| SYS-16 | Search projection và catalog query | Search, Restaurant, Kafka, Elasticsearch/Redis | active | Bounded restaurant/dish search; projection không được thay thế catalog/price authority |
| SYS-17 | Menu inventory reserve/commit/release | Restaurant, Order | gated | Không backorder; thiếu capacity fail-closed; bật phải có migration, race proof và rollback |
| SYS-18 | Serviceability và ETA decision | Restaurant, Routing, Order | gated | Polygon/coordinate/provider decision private; không tự fallback sang tọa độ trung tâm hoặc fee giả |
| SYS-19 | Voucher reservation và compensation | Promotion, Order, Saga | gated | Layer stacking/reservation/commit/release; default COD checkout chưa relay capability |
| SYS-20 | Flash-sale stock reservation và schedule | Flash Sale, Redis, Order, Kafka | gated | Atomic reserve/release, campaign schedule, cancellation/payment-failure compensation |
| SYS-21 | Analytics projection và reconciliation | Analytics, Kafka, PostgreSQL | experimental | Dashboard/admin/restaurant projection và reconcile job chưa là public MVP evidence |
| SYS-22 | Livestream provider/session/product boundary | Livestream, Agora, Restaurant, Customer | experimental | Room lifecycle, token, pinned product và moderation cần provider/ownership/runtime proof |
| SYS-23 | Online payment, refund executor và payout provider | Settlement, payment provider | decision-required | Provider callback/reconciliation/refund/payout policy chưa đủ để mở public mutation |
| SYS-24 | Marketing notification preference/dispatch | Notification, Firebase/provider | gated | Transactional notification không opt-out; marketing preference/dispatch vẫn default-off |
| SYS-25 | POD, failed delivery và return-to-restaurant | Delivery, object storage, Settlement | gated | Signed private proof, một retry/return state, manual-review boundary; flags mặc định false |
| SYS-26 | Simulator run, trace và reconcile | Simulator, delivery_simulator_web, Kafka | dev-only | Validate/start/pause/resume/abort/reconcile/algorithm trace; không quyết định offer production |
| SYS-27 | Runtime observe, backup/restore và recovery | Config, Eureka, Actuator, Prometheus/Grafana, runbooks | internal | Health/readiness, correlation/metrics, backup/restore/release gates; không phải business client contract |

## 8. Failure và alternate-case coverage

Các nhánh dưới đây là một phần của use case, không phải “một thành công khác”.
Bất kỳ tài liệu/implement mới nào chạm các nhánh này phải giữ behavior tương ứng.

| Case | Kích hoạt | Hành vi bắt buộc | Kết quả |
| --- | --- | --- | --- |
| F-01 | Auth → User fail giữa hai request | Retry/resume bằng identity key, không tạo profile thứ hai | Registration retry-safe |
| F-02 | Email chưa verify hoặc token recovery hết hạn | Từ chối login/reset, hiển thị recovery path | Không activate sai identity |
| F-03 | Access token hết hạn | Refresh single-flight rồi retry một lần | Refresh fail thì clear session |
| F-04 | Token hợp lệ nhưng sai role/ownership | Resource service trả 403, không retry nghiệp vụ | Không lộ/cập nhật resource khác |
| F-05 | Search projection stale/rỗng | Không biến projection thành price/order truth; báo empty/degraded | Catalog authority vẫn ở Restaurant |
| F-06 | Quote hết hạn hoặc giá/menu đổi | Trả QUOTE_EXPIRED/PRICE_CHANGED và yêu cầu preview/confirm lại | Không tạo order với tiền cũ |
| F-07 | Idempotency key lặp exact | Trả lại order cũ, không side effect lần hai | Transport retry an toàn |
| F-08 | Idempotency key lặp payload khác hoặc receipt đang chạy | IDEMPOTENCY_KEY_REUSED/IDEMPOTENCY_IN_PROGRESS, fail closed | Không tạo duplicate |
| F-09 | Restaurant/menu/inventory/serviceability không hợp lệ | Reject trước order hoặc reservation; không fallback dữ liệu giả | Order không được nhận sai |
| F-10 | Restaurant reject hoặc customer cancellation | Event/outbox compensation idempotent; Saga chờ delivery cancellation ack nếu đã tạo delivery | Terminal state có nguyên nhân |
| F-11 | Shipper reject, offer timeout hoặc không tìm thấy | Release candidate, rematch theo deadline; phân biệt SHIPPER_NOT_FOUND với CANCELLED | Không offer stale |
| F-12 | Stale offer/generation hoặc shipper đang bận | Reject accept/update; giữ state hiện tại | Không chiếm hai delivery |
| F-13 | Socket mất/reconnect hoặc publisher cũ gửi ping | Reconnect generation mới; reject stale update; đọc latest state | Không ghi đè location mới |
| F-14 | FCM không đến hoặc process bị kill | Durable inbox/current-offer REST recovery | Notification không là nguồn sự thật |
| F-15 | delivery.completed duplicate/conflict/amount mismatch | Dedup exact duplicate; conflict rollback/retry/DLT/manual review | Không post ledger hai lần |
| F-16 | Gated/experimental provider hoặc optional service chưa sẵn sàng | Fail closed, hiển thị unavailable/retry; không dùng fixture như production truth | Không mở capability ngoài policy |
| F-17 | POD/exception sau pickup | Chỉ chạy khi flag + object-storage/provider proof; retry/return state rõ ràng | Không phát trạng thái legacy sai |
| F-18 | Simulation trace đến sớm/mất | Simulator giữ trace tạm hoặc không dùng trace cho matching | Tooling không ảnh hưởng production |

## 9. Coverage theo service và tài liệu chi tiết

| Service/boundary | Use-case group được bao phủ | Nguồn chi tiết |
| --- | --- | --- |
| Gateway + Auth + User | Identity, session, authorization, profile, address, account lifecycle | [Security](../system/security.md), [workflows](../system/workflows.md) |
| Restaurant | Catalog, menu, owner profile, ratings, restaurant decision, gated inventory/serviceability | [Service catalog](../system/service-catalog.md), [feature index](./features/README.md) |
| Order | Quote, create/read/cancel, immutable money/location snapshot, rating eligibility | [Order lifecycle](./features/order-lifecycle.md), [HTTP catalog](../system/api/http-contract-catalog.md) |
| Saga + Match + Delivery | Create delivery, find shipper, offer, rematch, lifecycle, batch/exception gates | [Delivery & matching](./features/delivery-matching.md), [system workflows](../system/workflows.md) |
| Tracking + Notification | Location room authorization, durable inbox, FCM wake-up, recovery | [Clients](../system/clients.md), [events/data](../system/events-and-data.md) |
| Settlement | COD eligibility, receipt/ledger/balance, admin reads, refund/payment/payout boundary | [Settlement](./features/settlement.md) |
| Search | Restaurant/dish query and entity-sync projection | [Service catalog](../system/service-catalog.md), [HTTP catalog](../system/api/http-contract-catalog.md) |
| Promotion + Flash Sale | Voucher/campaign management, reservation/stock compensation, checkout gates | [Voucher/flash-sale feature](./features/voucher-flashsale-checkout.md) |
| Analytics + Livestream | Dashboard/projection and provider/room/product surfaces | [Service catalog](../system/service-catalog.md) |
| Routing + control plane | Route/matrix/ETA provider boundary, Config/Eureka/observability/recovery | [Operations](../system/operations/README.md), [technology/tooling](../system/technology-and-tooling.md) |
| Simulator | Scenario validate/run/control, traces and reconciliation | [Simulator docs](../system/simulator/README.md) |

The generated HTTP contract currently groups the service-owned API surface; use
that catalog for exact verbs, request schemas, auth principal and response
shape. This inventory intentionally does not duplicate all operation rows, which
would create a second API authority.

## 10. Cách duy trì inventory

Khi thêm hoặc thay đổi behavior externally observable:

1. Cập nhật use case/status ở đây và tài liệu feature/workflow owner tương ứng.
2. Cập nhật API/event/client action contract nếu route, field, actor hoặc state
   transition thay đổi.
3. Nếu thay đổi cross-system, cập nhật một plan tại workspace
   docs/plans/active/ trước khi rollout.
4. Regenerate/check public handbook từ delivery_web:

   npm run handbook:docs:generate
   npm run handbook:docs:check

Docs Portal là snapshot read-only, không gọi backend live, không hiển thị secret,
customer data hoặc internal route như public contract. Nếu một mục chưa có
owner policy/proof, giữ status gated, experimental, not exposed hoặc
decision-required; không đổi sang active chỉ vì controller/schema tồn tại.
