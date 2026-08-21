# HTTP API Inventory

Ngày cập nhật inventory: 2026-08-09

Tài liệu này liệt kê toàn bộ method có mapping trong 17 service có controller.
`saga-orchestrator-service` không có HTTP controller. Danh sách được sinh trực
tiếp từ annotation Java và hiện có **173 method**.

Contract backend MVP được freeze ngày 2026-07-26 sau clean Gate B8, API surface
classification và full reactor 602 test. Các capability ghi hidden/disabled hoặc
`OPEN` không được client coi là public contract. Không xóa endpoint chỉ dựa trên
việc không thấy client gọi.

## Classification rules

- `public-client`: use case hợp lệ của customer/restaurant/shipper, đi qua Gateway;
  resource service xác thực Bearer token qua Auth JWKS nếu không phải read-only public catalog/search.
- `public-admin`: chỉ ADMIN; Gateway chỉ giới hạn route/method, resource service enforce JWT role/ownership.
- `internal`: không có public Gateway route; dùng service credential và idempotency
  tại boundary.
- `dev-only`: chỉ tạo bean/route dưới profile `dev`.
- `deprecated`: có compatibility window, migration note và test.
- `dead`: chỉ xóa sau polyrepo search, event dependency review và focused test.

## Immediate classification corrections

| Surface | Proposed class | Evidence/problem |
|---|---|---|
| exact order/delivery admin maintenance handlers | public-admin | Gateway allow-list theo path + method; resource service bắt JWT role `ADMIN`; không còn admin wildcard |
| `PUT /api/deliveries/{id}/status` | public-client/SHIPPER self | chỉ shipper đã assign được đi tuần tự `PICKED_UP -> DELIVERING -> DELIVERED`; ADMIN và generic assign/cancel/rematch bị chặn; exact same-state retry không ghi thêm outbox |
| `/api/auth/accounts/email/**` | dead/deleted | không có backend/client caller; account detail admin dùng ID, Auth→User linkage dùng provisioning response |
| `POST /api/users`, `GET /api/users/by-auth/**` | internal | đã bỏ khỏi Gateway và yêu cầu shared internal credential; chỉ exact `POST /api/users/registrations` là public handoff |
| `POST /api/internal/users/{userId}/block-status` | internal | web chỉ dùng Auth admin status API; Auth đồng bộ projection bằng `Internal-Token` và body `{adminId, blocked, reason}`, idempotent cho retry |
| `/api/restaurants/validate/**` | internal | đã ẩn khỏi Gateway; fail-closed bằng `Internal-Token` dùng chung với order-service |
| restaurant `/api/cache/**` | dead/deleted | không có Gateway/client/ops consumer; availability mutation và warmup controller/service graph đã xóa, canonical mutation tự đồng bộ cache |
| restaurant `/api/location/**` | dead/deleted | geocoding API không có consumer/product contract; controller/service/Mapbox backend dependency đã xóa |
| order-service `/api/dashboard/**` | dead/deleted | không có Gateway/client consumer; dashboard web dùng contract Analytics riêng, Order graph mồ côi đã xóa sau polyrepo proof |
| order shipper list + Order/Delivery bulk cancel | dead/deleted | zero polyrepo call-site; canonical shipper read thuộc Delivery, bulk mutation thiếu per-aggregate outbox/audit và có thể phá state convergence |
| `/api/match/**` | dead/deleted | matching do Kafka/Saga điều phối; debug controller không có consumer đã xóa sau polyrepo proof |
| tracking nearby/read/busy REST | dead/deleted | không có consumer; Match dùng Redis GEO replica, busy/available dùng Kafka status event nên diagnostics controller đã xóa |
| tracking own location update/offline | public-shipper | derive shipper identity từ JWT; update fail-closed với tọa độ/telemetry không hữu hạn và `isOnline` null/sai kiểu; raw WebSocket là transport realtime canonical |
| `POST /api/notifications/send` | internal | không có Gateway route; fail-closed bằng `Internal-Token` |
| `/api/flashsales/internal/**` | internal | Gateway route đã loại 2026-07-22; service credential còn OPEN |
| settlement fake confirm | dev-only | không còn Gateway route trong COD-first MVP |
| settlement customer refund status | public-client/USER | exact `GET /api/settlement/refunds/my` only; query scopes to the JWKS-authenticated actor and returns a safe status projection of existing cases. It cannot create, approve, execute or mutate a refund. |
| settlement hold/release | public-admin/internal | không còn Gateway route; chỉ admin read surface riêng còn public |
| settlement internal COD eligibility | internal | Match gọi bằng `Internal-Token`; không có Gateway route, secret rỗng fail-closed |
| livestream write/token routes | experimental/hidden | Gateway route đã đóng; ownership/token role chưa enforce đầy đủ |
| public catalog/search reads | public-client | restaurant/dish có input/page bounds; dead shipper Elasticsearch search/sync graph đã xóa |

## Actor, ownership and consumer review

Mỗi exact handler ở phần sau thuộc đúng một surface dưới đây. `Target` là contract
phải đạt ở wave tương ứng; `Current gap` là bằng chứng audit, không phải claim đã
sửa.

| Service/surface | Target actor and ownership | Known consumer | Class/disposition | Current gap |
|---|---|---|---|---|
| auth password register | anonymous; Auth owns immutable identity and opaque handoff | Flutter | public-client/keep | `POST /api/auth/register` chỉ tạo/resume Auth identity, trả `principalId` canonical cùng `authId` compatibility alias và signed 15-minute provisioning token; không gọi User trong request này |
| auth login/social/refresh | anonymous; refresh token rotation | cả 3 client | public-client/keep | Mỗi device là một token family; refresh token chỉ lưu SHA-256 fingerprint, rotate dưới row lock và consumed-token reuse revoke toàn family trước khi trả 401; ba client single-flight và bắt buộc lưu cặp token mới |
| auth forgot/reset + email verification | anonymous; exact one-time token owns account | all clients | public-client/keep | uniform request response; AWS SES SMTP async after commit; token digest/expiry/consumption persisted; public-auth Gateway quota 10/min/IP; password reset revokes all refresh families/sessions |
| auth logout/sessions | authenticated account, own sessions | Flutter/web | public-client/keep | Logout bằng current/rotated refresh token và authenticated `DELETE /sessions/{deviceId}` revoke đúng device family; session khác không bị ảnh hưởng. Access token đã cấp vẫn stateless-valid tối đa 15 phút; immediate access invalidation không thuộc MVP. |
| auth account by id/admin actions | ADMIN | web admin | public-admin/keep | Auth resource server bắt `ADMIN`; Gateway chỉ route exact method. Block/unblock dùng account row lock transaction, revoke active sessions và ghi transactional `identity.status.changed` outbox trong event mode; User/Shipper project theo version/event receipt. Legacy Auth→User internal retry chỉ là event-off rollback rail. Block reason bound 500 và typed admin identity; compatibility list hard-cap 100, paginated envelope chờ client migration |
| auth account by email | authenticated service identity | auth→user/internal admin lookup | internal/keep | đã ẩn khỏi Gateway và fail-closed bằng shared secret; rotation/integration proof còn OPEN |
| user public registration handoff | anonymous signed provisioning JWT; identity verified locally through Auth JWKS | Flutter | public-client/keep | exact `POST /api/users/registrations`; client chỉ gửi signed provisioning token + profile, User derives identity locally, creates idempotently by principalId and atomically emits `identity.profile.created`; Auth links asynchronously through its consumer. No User→Auth registration RPC or Internal-Token callback exists in this public flow. |
| user internal create/by-auth | auth-service only | social/operator provisioning | internal/keep | không có Gateway route và bắt shared secret; create idempotent theo authId, từ chối rebinding theo authId hoặc email khác auth identity, DB migration khóa unique email case-insensitive; PostgreSQL concurrent proof còn OPEN |
| user current read/update | authenticated account owns its profile projection | Flutter/web/shipper | public-client/keep | canonical `GET/PUT /api/users` derive ID từ JWT actor đã được User resource server xác thực qua JWKS; path-ID mutation không còn public |
| user delete | auth-owned soft deactivate only | không có consumer | dead/deleted | user xác nhận không hard-delete nghiệp vụ; orphan `DELETE /api/users/{id}` controller/service/repository branch và feature flag đã xóa. Canonical admin block vô hiệu hóa Auth + User projection và revoke sessions; self-deactivate sau MVP phải được orchestration từ Auth, không được xóa profile trực tiếp. |
| user addresses | USER owns address and path userId; ADMIN support | Flutter | public-client/keep | exact path+method Gateway allow-list; User resource server/controller từ chối role khác dù numeric identity trùng; tất cả read/mutation đã đối chiếu owner; runtime self read 200 và cross-user read 403, spoof identity headers không đổi JWT actor; default mutation serialize bằng pessimistic owner lock |
| user admin statistics/list | ADMIN | web admin | public-admin/keep | exact GET Gateway allow-list + User resource server/controller bắt `ADMIN`; compatibility list hard-cap 100, paginated envelope chờ client migration |
| user block/unblock projection | Auth lifecycle event consumer | Auth `identity.status.changed` outbox | internal projection/keep | không có Gateway route. Auth là source-of-truth và User applies versioned, deduped Kafka projection; retry/DLT preserves version gaps/conflicts. Legacy internal block-status endpoint is retained only for event-off rollback and is not executed while Auth event mode is enabled. |
| restaurant public reads/search/menu available | anonymous read-only | Flutter/web | public-client/keep | exact GET allow-list, gồm cả Flash Sale public campaign/items; compatibility catalog/search/menu/rating lists cap 100; cache/location/validation không public. Flutter orphan `/restaurants/nearby` + `/restaurants/categories` graph đã xóa vì không có backend route/UI caller. |
| restaurant/menu writes and `my-*` | SHOP_OWNER owns restaurant/menu; ADMIN quản trị bằng entity route | web restaurant/admin | public-client+admin/keep | Gateway và service cùng yêu cầu `SHOP_OWNER` cho self list; mutation yêu cầu owner hoặc `ADMIN`; controller truyền trusted role xuống service, thiếu role fail-closed; DTO bounds trả 400 trước JPA |
| restaurant creator lookup | SHOP_OWNER self | web owner | public-self/keep | arbitrary creatorId endpoints removed; `my-restaurants`/`my-menu-items` là self contract canonical và bắt `SHOP_OWNER` ở Gateway + controller |
| restaurant atomic order validation | order-service credential | order checkout | internal/keep | chỉ còn `POST /api/restaurants/validate/order`; Gateway đã ẩn, restaurant fail-closed và order gửi cùng `INTERNAL_SECRET`; ba helper HTTP menu-item/total/hours không có consumer đã xóa, helper item validation giữ private |
| restaurant confirm/reject | SHOP_OWNER owns restaurant and order belongs to it | web restaurant | public-client/keep | Gateway + controller bắt role/owner; producer lưu decision + SHA-256 `payload_fingerprint` cùng outbox trong một transaction, nên duplicate replay khác payload bị chặn ngay cả khi outbox đã prune; internal Order eligibility khóa row order trước pending check để serialize với cancel đang chạy; order consumer đối chiếu restaurantId/order và idempotent; web dùng canonical `/api/restaurants/orders/{orderId}/confirm|reject` thay vì generic Order status mutation |
| restaurant ratings submit/read-own | USER owns delivered order / actor own | Flutter/web | public-client/keep | Gateway + controller bắt `USER`; internal order eligibility kiểm customer+restaurant+DELIVERED; DB unique `order_id` chống duplicate; service dùng `saveAndFlush` để convert duplicate sequential/concurrent thành HTTP 409 thay vì 500; PostgreSQL race proof còn OPEN |
| restaurant rating moderation | ADMIN | web admin | public-admin/keep | Gateway và controller cùng bắt `ADMIN`; focused authorization test xanh |
| cache mutation/warmup | none | không có polyrepo consumer | dead/deleted | controller/warmup graph đã xóa; canonical catalog mutation tự đồng bộ cache |
| geocode endpoints | none | không có polyrepo consumer | dead/deleted | controller/service/Mapbox backend dependency đã xóa; client map không phụ thuộc API này |
| order checkout/create/my-orders/detail/cancel | USER owns order; detail cho participant; ADMIN override detail/cancel only | Flutter/web | public-client/keep | checkout-preview/create/my-orders bắt đúng role USER ở Gateway và controller; preview trả server-issued `quoteId` + 5-minute expiry, create re-prices canonically and accepts UUID `Idempotency-Key` for transport retry (same principal/key/effective request returns original order; conflicting reuse/price/expiry are typed HTTP 409). Compatibility mode accepts requests missing both fields until `ORDER_QUOTE_ENFORCEMENT_ENABLED` is enabled. Preview và create cùng dùng atomic restaurant validation qua `Internal-Token`, không tự suy luận từ public catalog và fail-closed nếu thiếu pickup coordinate canonical; detail tiếp tục participant-scoped; cancel excludes SHIPPER; service ownership + pre-pickup state guard, pessimistic row lock + outbox; exact cancel retry cùng actor/reason no-op và không ghi outbox lần hai, replay khác actor/reason bị reject; shipper phải dùng Delivery cancel-assignment để availability/rematch hội tụ; canonical COD |
| order restaurant views | SHOP_OWNER owns restaurant | web restaurant | public-client/keep | chỉ `my-restaurant-orders` còn public và bắt `SHOP_OWNER` ở Gateway + controller; legacy arbitrary restaurantId/ownerId controller đã xóa hoàn toàn sau khi không còn consumer hợp lệ |
| order shipper view | none; Delivery owns fulfilment history | không có consumer | dead/deleted | arbitrary shipperId Order path/service/repository query đã xóa; shipper dùng scoped Delivery history/active APIs |
| order admin list/status | ADMIN | web admin | public-admin/keep | exact list/filter reads bắt `ADMIN`; unsafe bulk cancel và generic status/assign đã xóa khỏi controller public; admin recovery nếu bổ sung sau phải là công cụ riêng có audit |
| generic order update/delete/status/assign | no public actor | không còn consumer hợp lệ | dead/deleted | Gateway không route; service compatibility controller, DTO update và capability flags đã xóa. Web restaurant dùng canonical confirm/reject; shipper dùng Delivery cancel-assignment; admin recovery nếu bổ sung sau phải có authority/product audit riêng |
| order dashboard controller | none after analytics migration | không có polyrepo consumer | dead/deleted | controller/service/DTO/query graph và feature flag đã xóa; Analytics giữ capability riêng nhưng tiếp tục hidden |
| delivery legacy assign | none; Saga/Kafka is canonical | không có polyrepo consumer | dead/deleted | controller/DTO/service/repository branch và flag đã xóa; assignment chỉ qua one-offer accept |
| delivery accept | only currently offered SHIPPER; row lock/expiry | shipper app | public-client/keep | command DTO bắt positive order/canonical action, bounds pickup/location; đã khóa row, offered shipper + expiry và `saveAndFlush`; exact ACCEPT/REJECT HTTP replay trước rematch không phát event lần hai; PostgreSQL concurrency rehearsal còn OPEN |
| delivery current offer recovery | authenticated SHIPPER self | shipper app migrate trong client alignment phase | public-client/keep | exact `GET /api/deliveries/offers/current` derive shipper từ JWT, chỉ trả một offer chưa hết hạn và fail-closed nếu invariant one-offer bị vỡ; durable Notification inbox + FCM best-effort wake-up |
| delivery shipper history/active | authenticated SHIPPER self hoặc ADMIN support | shipper app dùng history/restore; admin chưa có call-site | public-client/keep | Gateway tách khỏi current-offer; service bắt cả role và path identity, không cho USER/SHOP_OWNER đi qua chỉ vì numeric ID trùng |
| delivery cancel-assignment | assigned SHIPPER, policy before pickup | shipper app cần dùng | public-client/keep | pessimistic order row lock serializes against pickup; reset assignment + AVAILABLE + shipper-rejected/rematch outboxes commit atomically; exact retry trước rematch trả state hiện tại và không phát event lần hai |
| delivery detail/order lookup | order customer, restaurant owner, assigned shipper, ADMIN | Flutter/web/shipper | public-client/keep | Delivery lưu riêng customer ID và server-validated restaurant-owner ID từ `order.created`; USER/SHOP_OWNER/SHIPPER đều scoped theo participant, legacy row thiếu owner field fail-closed; PostgreSQL migration proof OPEN |
| delivery tracking-access check | tracking-service credential | tracking raw WebSocket | internal/keep | shared secret, active delivery + assigned shipper + participant identity; không có Gateway route |
| delivery status update | assigned SHIPPER self | shipper app | public-client/restrict | Gateway exact SHIPPER role + service owner; chỉ tuần tự PICKED_UP→DELIVERING→DELIVERED, same-state retry no-op; PostgreSQL concurrency rehearsal còn OPEN |
| delivery shipper lists | SHIPPER self hoặc ADMIN | shipper app/web | public-client/keep | ownership đã enforce; history/active compatibility lists cap repository query ở 100 |
| delivery admin cancel-all | none | không có polyrepo consumer | dead/deleted | controller/service/repository query đã xóa vì có thể động tới pickup/delivering mà không có canonical per-delivery recovery/audit contract |
| shipper profile/create/update/online | SHIPPER self | shipper app | create/profile/update/online public; delete dead/deleted | delete API đã xóa sau zero-call-site proof; trusted user identity dùng typed Long; generic profile update không còn nhận `isOnline`; `PATCH /online-status?isOnline=false` gọi internal Tracking tombstone trước khi lưu projection, còn `true` chỉ là publisher intent chờ heartbeat GPS |
| shipper by id | ADMIN until a limited participant DTO exists | web admin only | public-admin/restrict | full DTO chứa giấy phép/CCCD nên không public cho customer. Flutter tracking không còn gọi `/shippers/{id}` hoặc giữ `in-area` discovery graph; customer chỉ dùng Delivery `shipperId` + participant raw location. |
| shipper all/online | ADMIN | web admin | public-admin/keep | Gateway/controller bắt ADMIN; fleet page và online compatibility list đều cap 100 |
| legacy shipper PostgreSQL location | none | không có polyrepo HTTP consumer | dead/deleted | controller/service/repository/entity/DTO/mapper đã xóa sau zero-call-site proof; legacy DB table được giữ để tránh drop dữ liệu không được authorize; Tracking Redis/raw WebSocket là canonical |
| shipper ratings | SHIPPER self read only hiện tại | shipper app | self read keep; write dead/deleted | arbitrary profile-ID read route đã xóa; `/me/ratings` bắt service-level SHIPPER, resolve JWT userId→profileId và trả `BaseResponse<List<ShipperRatingResponse>>`. Profile rating là null khi chưa có rating row; write controller đã xóa sau zero-call-site proof |
| tracking update/offline | SHIPPER self from JWT/socket session | shipper app | public-client/keep one transport | REST và raw socket derive identity từ JWT; client phải gửi Authorization khi handshake; explicit offline luôn phát timestamped tombstone sang Match |
| tracking internal offline command | shipper-service credential | shipper-service | internal/keep | `POST /api/tracking/internal/shippers/{shipperId}/offline` chỉ nhận `Internal-Token`, không có Gateway route, và là authority để Match loại shipper khỏi availability |
| tracking socket subscribe | active order participants | Flutter tracking | public-client/keep | socket bắt `deliveryId` và internal Delivery participant check; arbitrary REST point-read đã bỏ khỏi public edge |
| tracking online/nearby/distance diagnostics | none | không có consumer | dead/deleted | HTTP controller và dead Redis query/health helpers đã xóa; matching đọc replica riêng trong Match |
| match busy/available replica | Kafka status event | delivery lifecycle | internal-event/keep | REST mutation và Tracking write-only consumer đã xóa; Match là consumer duy nhất, áp dụng offer release + BUSY/AVAILABLE + timestamp/event fence atomically trong Redis |
| match nearby | Saga/match internal | Saga command listener | internal-event/keep | HTTP debug controller đã xóa; `saga.command.find-shipper` là ingress canonical |
| notification FCM token register/remove | authenticated user owns token | Flutter/shipper | public-client/keep | Gateway chỉ route đúng hai POST register/unregister; Notification resource server derive JWT actor qua JWKS; Redis Lua reverse-owner ngăn một token thuộc nhiều account, Redis integration/race proof còn OPEN |
| notification list/unread/read/delete | authenticated user owns notification | cả 3 client | public-client/keep | ownership scope tới repository; list/unread cap 100, unread-count vẫn exact DB count; delivery status inbox copy không synthesize `shipperName` khi event không sở hữu field này |
| notification send | service identity only | internal operator/service | internal/keep | không có Gateway route; shared secret + validated bounded command, unexpected error sanitized |
| settlement own balances/transactions | SHOP_OWNER/SHIPPER owns entity | web/shipper | hidden/disabled pending ownership | arbitrary entityId endpoints không có Gateway route và self-service controllers mặc định không tạo bean (`SETTLEMENT_SELF_SERVICE_API_ENABLED=false`) |
| settlement withdrawal | owner requests; ADMIN approves/rejects | web/shipper | hidden/disabled + public-admin | self request controller mặc định tắt; admin read xử lý record hiện hữu, concurrency proof còn thiếu |
| settlement hold/release/deposit | internal ledger workflow or ADMIN | payment/delivery/admin | hidden/disabled | manual money mutation nằm trong self-service controller mặc định tắt; canonical COD listener/internal eligibility vẫn hoạt động |
| settlement COD eligibility | match-service credential; shipper + positive canonical COD amount | match | internal/keep | exact internal endpoint dùng shared secret; nearest candidate thiếu ký quỹ bị bỏ qua, lỗi Settlement không bị đổi thành `shipper.not-found`; completion ledger chỉ nhận exact COD và fail-closed với identity/totals/commissions không canonical |
| settlement admin surfaces | ADMIN | web admin | public-admin/read-only | exact GET balances/transactions/pending/revenue/refund queue; refund case detail/list are read-only and capped; compatibility lists cap 100, aggregate revenue DB-side; approve/reject/reverse tách sang controller mặc định tắt (`SETTLEMENT_ADMIN_MUTATION_API_ENABLED=false`) |
| payment create/status/provider | authenticated payer owns payment | cả 3 client còn reference legacy | hidden/disabled | không có Gateway route, controller off mặc định và Order payment-event listener cũng off (`ORDER_PAYMENT_EVENT_PROCESSING_ENABLED=false`) trong COD-first MVP |
| VNPAY callback/IPN | payment provider signature | provider | hidden/disabled-until-verified | toàn payment bean graph off mặc định; provider không có DEMO credential và fail-closed khi env thiếu; callback/reconciliation proof OPEN |
| fake payment confirm | developer | no production client | hidden/dev-test-only | không có production Gateway route; controller/provider cần explicit processing+fake flags và active profile `dev|test`, không thể bật ở `prod` |
| promotion collect/my/calculate | USER own voucher/order input canonical | Flutter/web | collect/my public; calculate hidden | Promotion resource server bắt USER cho collect/my; collect kiểm active window, concurrent duplicate trả 409 qua DB unique; wallet cap 100. Calculate là `/api/promotions/internal/calculate`, chỉ Order gọi bằng `Internal-Token` với `userId` trusted trong body, và checkout flag vẫn off; calculate batch lookup nhưng chưa tính discount |
| promotion merchant CRUD/list | SHOP_OWNER owns merchant/restaurant | web restaurant | GET list public; create hidden/disabled | create không có public Gateway route và không có active controller bean mặc định (`PROMOTION_MERCHANT_CREATE_API_ENABLED=false`); Gateway test chặn `POST /api/promotions/merchant`, chỉ giữ `GET /api/promotions/merchant` cho SHOP_OWNER. Cần explicit restaurantId + ownership proof trước khi mở lại |
| promotion platform/admin list/delete | ADMIN | web admin | public-admin/keep | Gateway/controller bắt `ADMIN`; exact POST/GET/DELETE methods, list cap 100; concurrency còn OPEN |
| promotion reserve | order-service credential | order checkout | internal/disabled-for-MVP | hidden + shared secret; feature flag false và listener compensation không tạo bean; request IDs được validate sau credential/recovery gate. Focused controller test 2026-07-29 xác nhận credential được kiểm trước checkout flag/validation và disabled path không gọi service. Cần reservation record/outbox trước khi mở |
| flash-sale public campaigns/items | anonymous read-only | Flutter/web | public-client/keep | exact path+GET public, không còn wildcard; active campaign và approved-item lists cap 100 |
| flash-sale merchant item | SHOP_OWNER owns restaurant/item | web restaurant | disabled/hidden | Gateway route removed; controller feature flag false because canonical menu/price proof and checkout recovery are OPEN. Context test 2026-07-29 xác nhận merchant controller không tạo bean mặc định; nếu bật flag, controller bắt SHOP_OWNER và gọi internal Restaurant ownership check bằng trusted merchant ID |
| flash-sale admin campaign/approve | ADMIN | web admin | public-admin/keep | exact path+method Gateway allow-list và controller cùng bắt `ADMIN`; campaign name bound 255 và status dùng typed enum để malformed value trả 400 |
| flash-sale reserve | order-service credential + idempotency key | order checkout | internal/disabled-for-MVP | hidden + shared secret; feature flag false và compensation listener disabled; item/quantity/price được validate sau credential/recovery gate. Focused controller/context proof 2026-07-29 xác nhận Gateway không route internal reserve, service kiểm credential trước flag/validation, disabled path không lấy stock service, và `FlashSaleStockService`/RedisConfig không tạo bean mặc định. Reservation record/idempotency/partial rollback OPEN |
| search restaurants/dishes | anonymous read-only | Flutter/web | public-client/keep | exact GET; query 1-100 chars, page >=0, size 1-100; disabled profile trả empty không phải functional proof; double `/api` in Flutter |
| search shippers | none; admin fleet uses shipper-service | không có polyrepo consumer | dead/deleted | Gateway và clients không có caller; controller/index repository/document/consumer branch cùng Shipper fire-and-forget publisher đã xóa. Matching tiếp tục dùng Redis GEO, admin fleet dùng scoped Shipper API. |
| analytics admin dashboard/reconcile | ADMIN | web admin | experimental/hidden | Gateway route đã đóng; controller/listener/job không tạo bean mặc định; enabled-mode còn thiếu role, dedup và retry/DLT proof |
| analytics restaurant dashboards | SHOP_OWNER owns restaurant or ADMIN | web restaurant | experimental/hidden | Gateway route đã đóng và capability off mặc định; arbitrary restaurantId/fallback userId-as-restaurantId chưa đạt ownership |
| livestream active/detail/join/products read | authenticated/public viewer per product policy | Flutter/web | experimental/disabled | Gateway surface đóng và toàn bộ service HTTP controllers mặc định không tạo bean (`LIVESTREAM_API_ENABLED=false`) trong COD MVP; client UI là migration item |
| livestream create/start/end/product writes | SHOP_OWNER owns restaurant/live | web restaurant | experimental/disabled | service HTTP controllers mặc định tắt; create vẫn chưa chứng minh seller sở hữu restaurant nên không được mở |
| livestream token | authenticated viewer; channel/role/expiry server-derived | Flutter/web | experimental/disabled | Gateway route và service controller cùng đóng; caller-controlled role/channel boundary còn OPEN |

## Exact method inventory

| Service | Controller | Verb | Path | Handler |
|---|---|---|---|---|
| analytics-service | DashboardController | GET | `/api/analytics/dashboard/admin` | `getAdminDashboard` |
| analytics-service | DashboardController | GET | `/api/analytics/dashboard/restaurant/{restaurantId}` | `getRestaurantDashboard` |
| analytics-service | DashboardController | GET | `/api/analytics/dashboard/my-restaurant` | `getMyRestaurantDashboard` |
| analytics-service | DashboardController | POST | `/api/analytics/reconcile` | `manualReconcile` |
| auth-service | AuthController | POST | `/api/auth/register` | `register` |
| auth-service | AuthController | GET | `/api/auth/registrations/{handle}` | `registrationStatus` |
| auth-service | AuthController | POST | `/api/auth/login` | `login` |
| auth-service | AuthController | POST | `/api/auth/social-login` | `socialLogin` |
| auth-service | AuthController | POST | `/api/auth/refresh-token` | `refreshToken` |
| auth-service | AuthController | POST | `/api/auth/logout` | `logout` |
| auth-service | AuthController | POST | `/api/auth/forgot-password` | `forgotPassword` |
| auth-service | AuthController | POST | `/api/auth/reset-password` | `resetPassword` |
| auth-service | AuthController | POST | `/api/auth/email-verification/request` | `requestEmailVerification` |
| auth-service | AuthController | POST | `/api/auth/email-verification/confirm` | `confirmEmailVerification` |
| auth-service | AuthController | GET | `/api/auth/sessions` | `getSessions` |
| auth-service | AuthController | DELETE | `/api/auth/sessions/{deviceId}` | `revokeDeviceSession` |
| auth-service | AuthController | GET | `/api/auth/accounts/{id}` | `getAccountById` |
| auth-service | AuthController | POST | `/api/auth/admin/accounts/{id}/block` | `blockAccount` |
| auth-service | AuthController | POST | `/api/auth/admin/accounts/{id}/unblock` | `unblockAccount` |
| auth-service | JwksController | GET | `/.well-known/jwks.json` | `getJwks` |
| delivery-service | DeliveryController | POST | `/api/deliveries/accept` | `acceptDelivery` |
| delivery-service | DeliveryController | POST | `/api/deliveries/cancel-assignment` | `cancelAssignedDelivery` |
| delivery-service | DeliveryController | GET | `/api/deliveries/offers/current` | `getCurrentOffer` |
| delivery-service | DeliveryController | GET | `/api/deliveries/{id}` | `getDelivery` |
| delivery-service | DeliveryController | PUT | `/api/deliveries/{id}/status` | `updateStatus` |
| delivery-service | DeliveryController | GET | `/api/deliveries/shipper/{shipperId}` | `getDeliveriesByShipper` |
| delivery-service | DeliveryController | GET | `/api/deliveries/shipper/{shipperId}/active` | `getActiveDeliveriesByShipper` |
| delivery-service | DeliveryController | GET | `/api/deliveries/order/{orderId}` | `getDeliveryByOrderId` |
| delivery-service | InternalDeliveryController | GET | `/api/deliveries/internal/{deliveryId}/tracking-access` | `canTrack` |
| flashsale-service | AdminFlashSaleController | POST | `/api/flashsales/admin/campaigns` | `createCampaign` |
| flashsale-service | AdminFlashSaleController | GET | `/api/flashsales/admin/campaigns` | `getAllCampaigns` |
| flashsale-service | AdminFlashSaleController | GET | `/api/flashsales/admin/campaigns/{id}/items` | `getCampaignItems` |
| flashsale-service | AdminFlashSaleController | PUT | `/api/flashsales/admin/campaigns/{id}/status` | `updateStatus` |
| flashsale-service | AdminFlashSaleController | PUT | `/api/flashsales/admin/items/{id}/approve` | `approveItem` |
| flashsale-service | InternalFlashSaleController | POST | `/api/flashsales/internal/reserve` | `reserveStock` |
| flashsale-service | InternalFlashSaleController | POST | `/api/flashsales/internal/quote` | `quote` |
| flashsale-service | InternalFlashSaleController | POST | `/api/flashsales/internal/reservations/{reservationId}/commit` | `commit` |
| flashsale-service | InternalFlashSaleController | POST | `/api/flashsales/internal/reservations/{reservationId}/release` | `release` |
| flashsale-service | MerchantFlashSaleController | POST | `/api/flashsales/merchant/items` | `registerItem` |
| flashsale-service | PublicFlashSaleController | GET | `/api/flashsales/public/campaigns` | `getActiveCampaigns` |
| flashsale-service | PublicFlashSaleController | GET | `/api/flashsales/public/campaigns/{campaignId}/items` | `getItems` |
| livestream-service | LivestreamController | POST | `/api/livestreams` | `createLivestream` |
| livestream-service | LivestreamController | POST | `/api/livestreams/{id}/start` | `startLivestream` |
| livestream-service | LivestreamController | POST | `/api/livestreams/{id}/join` | `joinLivestream` |
| livestream-service | LivestreamController | POST | `/api/livestreams/{id}/end` | `endLivestream` |
| livestream-service | LivestreamController | GET | `/api/livestreams/active` | `getActiveLivestreams` |
| livestream-service | LivestreamController | GET | `/api/livestreams/{id}` | `getLivestreamById` |
| livestream-service | LivestreamController | GET | `/api/livestreams/seller/{sellerId}` | `getLivestreamsBySeller` |
| livestream-service | LivestreamController | GET | `/api/livestreams/restaurant/{restaurantId}` | `getLivestreamsByRestaurant` |
| livestream-service | LivestreamProductController | POST | `/api/livestreams/{id}/products/pin` | `pinProduct` |
| livestream-service | LivestreamProductController | DELETE | `/api/livestreams/{id}/products/{productId}/pin` | `unpinProduct` |
| livestream-service | LivestreamProductController | DELETE | `/api/livestreams/{id}/products/{productId}` | `removeProduct` |
| livestream-service | LivestreamProductController | GET | `/api/livestreams/{id}/products` | `getProductsByLivestream` |
| livestream-service | LivestreamProductController | GET | `/api/livestreams/{id}/products/pinned` | `getPinnedProducts` |
| livestream-service | StreamTokenController | POST | `/api/livestreams/{id}/token` | `generateToken` |
| notification-service | FirebaseController | POST | `/api/firebase/register-token` | `registerFcmToken` |
| notification-service | FirebaseController | POST | `/api/firebase/unregister-token` | `unregisterFcmToken` |
| notification-service | NotificationController | POST | `/api/notifications/send` | `sendNotification` |
| notification-service | NotificationController | GET | `/api/notifications/user/{userId}` | `getUserNotifications` |
| notification-service | NotificationController | GET | `/api/notifications/unread` | `getUnreadNotifications` |
| notification-service | NotificationController | GET | `/api/notifications/unread-count` | `getUnreadCount` |
| notification-service | NotificationController | PUT | `/api/notifications/{id}/read` | `markAsRead` |
| notification-service | NotificationController | PUT | `/api/notifications/mark-all-read` | `markAllAsRead` |
| notification-service | NotificationController | GET | `/api/notifications/{id}` | `getNotificationById` |
| notification-service | NotificationController | DELETE | `/api/notifications/{id}` | `deleteNotification` |
| order-service | OrderController | POST | `/api/orders/checkout-preview` | `checkoutPreview` |
| order-service | OrderController | POST | `/api/orders` | `createOrder` |
| order-service | OrderController | GET | `/api/orders/{id}` | `getOrderById` |
| order-service | OrderController | GET | `/api/orders/my-orders` | `getMyOrders` |
| order-service | OrderController | GET | `/api/orders/my-restaurant-orders` | `getMyRestaurantOrders` |
| order-service | OrderController | GET | `/api/orders/status/{status}` | `getOrdersByStatus` |
| order-service | OrderController | GET | `/api/orders/all` | `getAllOrders` |
| order-service | OrderController | PUT | `/api/orders/{id}/cancel` | `cancelOrder` |
| order-service | InternalOrderController | GET | `/api/orders/internal/{orderId}/rating-eligibility` | `isRatingEligible` |
| order-service | InternalOrderController | GET | `/api/orders/internal/{orderId}/restaurant-decision-eligibility` | `isRestaurantDecisionEligible` |
| promotion-service | PromotionController | POST | `/api/promotions/platform` | `createPlatformVoucher` |
| promotion-service | PromotionController | POST | `/api/promotions/collect/{code}` | `collectVoucher` |
| promotion-service | PromotionController | GET | `/api/promotions/my-vouchers` | `getMyVouchers` |
| promotion-service | PromotionController | GET | `/api/promotions/merchant` | `listMerchantVouchers` |
| promotion-service | PromotionController | GET | `/api/promotions/admin` | `listAllVouchers` |
| promotion-service | PromotionController | DELETE | `/api/promotions/{id}` | `deleteVoucher` |
| promotion-service | PromotionController | POST | `/api/promotions/internal/calculate` | `calculate` |
| promotion-service | PromotionController | POST | `/api/promotions/internal/reserve` | `reserve` |
| promotion-service | PromotionController | POST | `/api/promotions/internal/reservations/{reservationId}/commit` | `commit` |
| promotion-service | PromotionController | POST | `/api/promotions/internal/reservations/{reservationId}/release` | `release` |
| restaurant-service | MenuItemController | POST | `/api/menu-items` | `create` |
| restaurant-service | MenuItemController | PUT | `/api/menu-items/{id}` | `update` |
| restaurant-service | MenuItemController | DELETE | `/api/menu-items/{id}` | `delete` |
| restaurant-service | MenuItemController | GET | `/api/menu-items/restaurant/{restaurantId}` | `getByRestaurant` |
| restaurant-service | MenuItemController | GET | `/api/menu-items/restaurant/{restaurantId}/available` | `getAvailableItems` |
| restaurant-service | MenuItemController | GET | `/api/menu-items/my-menu-items` | `getMyMenuItems` |
| restaurant-service | OrderValidationController | POST | `/api/restaurants/validate/order` | `validateOrder` |
| restaurant-service | InternalRestaurantController | GET | `/api/restaurants/internal/{restaurantId}/owners/{ownerId}` | `isOwnedBy` |
| restaurant-service | RestaurantController | POST | `/api/restaurants` | `create` |
| restaurant-service | RestaurantController | PUT | `/api/restaurants/{id}` | `update` |
| restaurant-service | RestaurantController | DELETE | `/api/restaurants/{id}` | `delete` |
| restaurant-service | RestaurantController | GET | `/api/restaurants/{id}` | `getById` |
| restaurant-service | RestaurantController | GET | `/api/restaurants` | `getAll` |
| restaurant-service | RestaurantController | GET | `/api/restaurants/search` | `search` |
| restaurant-service | RestaurantController | GET | `/api/restaurants/my-restaurants` | `getMyRestaurants` |
| restaurant-service | RestaurantOrderController | POST | `/api/restaurants/orders/{orderId}/confirm` | `confirmOrder` |
| restaurant-service | RestaurantOrderController | POST | `/api/restaurants/orders/{orderId}/reject` | `rejectOrder` |
| restaurant-service | RestaurantRatingController | POST | `/api/restaurants/{restaurantId}/ratings` | `submitRating` |
| restaurant-service | RestaurantRatingController | GET | `/api/restaurants/{restaurantId}/ratings` | `getRestaurantRatings` |
| restaurant-service | RestaurantRatingController | GET | `/api/restaurants/me/ratings` | `getMyRatings` |
| restaurant-service | RestaurantRatingController | GET | `/api/restaurants/admin/ratings` | `getAllRatings` |
| restaurant-service | RestaurantRatingController | PUT | `/api/restaurants/admin/ratings/{id}/status` | `updateRatingStatus` |
| search-service | SearchController | GET | `/api/search/restaurants` | `searchRestaurants` |
| search-service | SearchController | GET | `/api/search/dishes` | `searchDishes` |
| settlement-service | AdminController | GET | `/api/settlement/admin/balances` | `getAllBalances` |
| settlement-service | AdminController | GET | `/api/settlement/admin/transactions` | `getAllTransactions` |
| settlement-service | AdminController | GET | `/api/settlement/admin/transactions/pending` | `getPendingWithdrawals` |
| settlement-service | AdminController | GET | `/api/settlement/admin/revenue` | `getPlatformRevenue` |
| settlement-service | RefundAdminController | GET | `/api/settlement/admin/refunds` | `list` |
| settlement-service | RefundAdminController | GET | `/api/settlement/admin/refunds/{refundId}` | `get` |
| settlement-service | RefundCustomerController | GET | `/api/settlement/refunds/my` | `list` |
| settlement-service | BalanceController | GET | `/api/settlement/balances/restaurant/{entityId}` | `getRestaurantBalance` |
| settlement-service | BalanceController | GET | `/api/settlement/balances/shipper/{entityId}` | `getShipperBalance` |
| settlement-service | BalanceController | GET | `/api/settlement/balances/restaurant/{entityId}/earnings` | `getRestaurantEarnings` |
| settlement-service | BalanceController | GET | `/api/settlement/balances/shipper/{entityId}/earnings` | `getShipperEarnings` |
| settlement-service | BalanceController | POST | `/api/settlement/balances/restaurant/{entityId}/withdraw` | `requestRestaurantWithdrawal` |
| settlement-service | BalanceController | POST | `/api/settlement/balances/shipper/{entityId}/withdraw` | `requestShipperWithdrawal` |
| settlement-service | BalanceController | POST | `/api/settlement/balances/shipper/{entityId}/hold` | `holdShipperBalance` |
| settlement-service | BalanceController | POST | `/api/settlement/balances/shipper/{entityId}/release` | `releaseShipperBalance` |
| settlement-service | BalanceController | POST | `/api/settlement/balances/shipper/{entityId}/deposit` | `topUpDeposit` |
| settlement-service | BalanceController | GET | `/api/settlement/balances/shipper/{entityId}/cod-eligibility` | `checkCodEligibility` |
| settlement-service | InternalSettlementController | GET | `/api/settlement/internal/shippers/{shipperId}/cod-eligibility` | `isCodEligible` |
| settlement-service | PaymentController | POST | `/api/settlement/payments/create` | `createPayment` |
| settlement-service | PaymentController | GET | `/api/settlement/payments/vnpay-callback` | `vnpayCallback` |
| settlement-service | PaymentController | GET\|POST | `/api/settlement/payments/vnpay-ipn` | `vnpayIpn` |
| settlement-service | FakePaymentController | GET | `/api/settlement/payments/fake-confirm/{paymentRef}` | `fakeConfirm` |
| settlement-service | PaymentController | GET | `/api/settlement/payments/{paymentId}` | `getPaymentStatus` |
| settlement-service | PaymentController | GET | `/api/settlement/payments/ref/{paymentRef}` | `getPaymentByRef` |
| settlement-service | PaymentController | GET | `/api/settlement/payments/providers` | `getAvailableProviders` |
| settlement-service | TransactionController | GET | `/api/settlement/transactions/restaurant/{entityId}` | `getRestaurantTransactions` |
| settlement-service | TransactionController | GET | `/api/settlement/transactions/shipper/{entityId}` | `getShipperTransactions` |
| settlement-service | TransactionController | GET | `/api/settlement/transactions/{id}` | `getTransactionById` |
| simulator-service | SimulatorController | POST | `/api/simulator/validate` | `validate` |
| simulator-service | SimulatorController | POST | `/api/simulator/runs` | `start` |
| simulator-service | SimulatorController | GET | `/api/simulator/runs/{runId}` | `get` |
| simulator-service | SimulatorController | GET | `/api/simulator/runs/{runId}/stream` | `stream` |
| simulator-service | SimulatorController | POST | `/api/simulator/runs/{runId}/pause` | `pause` |
| simulator-service | SimulatorController | POST | `/api/simulator/runs/{runId}/resume` | `resume` |
| simulator-service | SimulatorController | POST | `/api/simulator/runs/{runId}/abort` | `abort` |
| simulator-service | SimulatorController | DELETE | `/api/simulator/runs/{runId}` | `cleanup` |
| shipper-service | ShipperController | POST | `/api/shippers` | `create` |
| shipper-service | ShipperController | GET | `/api/shippers/my-profile` | `getMyProfile` |
| shipper-service | ShipperController | PUT | `/api/shippers` | `update` |
| shipper-service | ShipperController | PATCH | `/api/shippers/online-status` | `updateOnlineStatus` |
| shipper-service | ShipperController | GET | `/api/shippers/{id}` | `getById` |
| shipper-service | ShipperController | GET | `/api/shippers` | `getAll` |
| shipper-service | ShipperController | GET | `/api/shippers/online` | `getOnlineShippers` |
| shipper-service | ShipperRatingController | GET | `/api/shippers/me/ratings` | `getMyRatings` |
| tracking-service | ShipperLocationController | POST | `/api/tracking/shipper-locations/update` | `updateLocation` |
| tracking-service | ShipperLocationController | POST | `/api/tracking/shipper-locations/offline` | `markOffline` |
| tracking-service | InternalShipperAvailabilityController | POST | `/api/tracking/internal/shippers/{shipperId}/offline` | `markOffline` |
| tracking-service | InternalLocationHistoryController | GET | `/internal/tracking/location-history/deliveries/{deliveryId}` | `byDelivery` |
| user-service | UserAddressController | GET | `/api/addresses/users/{userId}/addresses` | `getUserAddresses` |
| user-service | UserAddressController | GET | `/api/addresses/{id}` | `getAddress` |
| user-service | UserAddressController | POST | `/api/addresses/users/{userId}/addresses` | `createAddress` |
| user-service | UserAddressController | PUT | `/api/addresses/{id}` | `updateAddress` |
| user-service | UserAddressController | DELETE | `/api/addresses/{id}` | `deleteAddress` |
| user-service | UserAddressController | PATCH | `/api/addresses/{id}/default` | `setDefault` |
| user-service | UserController | POST | `/api/users` | `createUser` |
| user-service | UserController | POST | `/api/users/registrations` | `registerUser` |
| user-service | UserController | GET | `/api/users` | `getCurrentUser` |
| user-service | UserController | GET | `/api/users/by-auth/{authId}` | `getUserByAuthId` |
| user-service | UserController | PUT | `/api/users` | `updateCurrentUser` |
| user-service | UserController | GET | `/api/users/admin/statistics` | `getUserStatistics` |
| user-service | UserController | GET | `/api/users/admin/all` | `getAllUsers` |
| user-service | UserController | POST | `/api/users/admin/{userId}/block` | `blockUser` |
| user-service | UserController | POST | `/api/users/admin/{userId}/unblock` | `unblockUser` |
| user-service | InternalUserBlockStatusController | POST | `/api/internal/users/{userId}/block-status` | `synchronizeBlockStatus` |
