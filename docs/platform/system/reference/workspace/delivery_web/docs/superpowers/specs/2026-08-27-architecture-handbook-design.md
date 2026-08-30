# Architecture Handbook Design

## Goal

Biến `/system-overview/architecture` thành một trang bản đồ kiến trúc hệ thống
đọc được ở cấp tổng thể, có thể khám phá từng node và connection, nhưng vẫn
giữ đúng handbook public read-only và dữ liệu snapshot hiện hành.

## Scope

### In scope

- Canvas diagram tương tác cho client, API Gateway, domain service, data/event
  infrastructure và private control plane.
- Tự động xếp layout theo layer để hạn chế node và đường nối chồng chéo.
- Node và edge có trạng thái chọn, hover highlight và inspector chi tiết.
- Tìm kiếm node, lọc layer/status, fit view, zoom/pan và minimap.
- Chế độ mặc định `Toàn cảnh hệ thống`; flow overlay chỉ là lớp highlight tùy
  chọn, không thay thế system map.
- Fallback mobile bằng layer accordion và danh sách connection.
- UI tiếng Việt; chỉ giữ tiếng Anh cho tên service/client, API path, event,
  topic, queue, database và identifier kỹ thuật.

### Out of scope

- Không gọi backend live, không mutation và không có Try-it API.
- Không thêm service, API, event, database hay connection không có trong nguồn
  canonical.
- Không thay đổi Flow Explorer, Services & Contracts, Overview hoặc Docs Portal.
- Không biến canvas thành editor cho phép sửa topology.
- Không hiển thị secret, credential, hostname môi trường hoặc thông tin vận hành
  riêng tư không được allowlist.

## Source of truth

- Node và connection: `src/modules/admin/data/systemOverviewData.ts` với
  `SYSTEM_NODES` và `SYSTEM_CONNECTIONS`.
- Flow highlight: `src/modules/system-handbook/data/flowExplorerData.ts`.
- Ngữ nghĩa layer, trust boundary và edge: `docs/system/architecture.md` và
  `docs/system/diagram-standards.md`.
- `dataStore` trên service node được hiển thị như thông tin data ownership.
  Chỉ connection trong `SYSTEM_CONNECTIONS` mới được vẽ thành edge; không suy
  diễn thêm database edge từ chuỗi `dataStore`.

## Technical approach

Use `@xyflow/react` làm renderer/interactions và `elkjs` làm layout engine.
React Flow cung cấp canvas, custom node/edge, selection, zoom, pan, controls,
background và minimap. ELK chạy layout layered theo graph có nhóm/layer; kết quả
được chuyển thành positions cho React Flow. Layout chạy từ snapshot local và có
fallback positions ổn định nếu layout promise lỗi.

Dependencies được pin theo registry tại thời điểm thiết kế:

- `@xyflow/react@12.11.5`
- `elkjs@0.12.0`

Không thêm UI kit mới; dùng Tailwind và icon system hiện tại của `delivery_web`.

## Visual design

### Page shell

- Heading: `Kiến trúc hệ thống`
- Supporting copy: `Bản đồ các khối, ranh giới sở hữu dữ liệu và cách các thành
  phần giao tiếp trong snapshot hiện hành.`
- Badge trạng thái: `As-built · Snapshot hiện tại`.
- Khu vực diagram là surface chính, không bọc thêm nhiều lớp card lồng nhau.
- Typography phải đạt mức dễ đọc hiện tại của Overview; tên kỹ thuật dùng
  monospace nhưng không nhỏ hơn 12px trên desktop và 12px trên mobile.

### Layer order

Canvas dùng hướng trái → phải theo các boundary sau:

1. `Client applications`
2. `Public application edge`
3. `Private domain services`
4. `State & event infrastructure`
5. `Private control plane`

Các nhóm trên chỉ là cách trình bày lại các node canonical. Không dùng
subgraph làm endpoint của edge.

### Node styles

- Client: nền xanh lam nhạt, icon thiết bị, label client kỹ thuật và vai trò.
- Gateway/edge: accent cam, nhấn mạnh đây là public edge duy nhất.
- Domain service: nền trắng, accent theo `group`, hiển thị tên kỹ thuật, vai trò
  ngắn, status và data owner.
- Infrastructure: nền xám/xanh đậm hơn, icon database/event/cache/provider;
  hiển thị boundary private/external.
- Selected node: viền accent + ring rõ ràng; active flow node: accent rõ; node
  không thuộc flow: giảm opacity vừa phải nhưng vẫn đọc được.
- Status không chỉ phụ thuộc màu: luôn có text `Active`, `Private`, `Hidden`
  hoặc `Test-only`.

### Edge styles

- `sync`: đường liền, mũi tên rõ.
- `async`: đường nét đứt, accent tím.
- `storage`: đường liền mảnh, accent xanh lá.
- `external`: đường liền/dashed theo dữ liệu và accent xanh dương.
- `control`: đường nét đứt mảnh, accent xám/tím.

Label edge chỉ hiện đầy đủ khi edge được chọn hoặc khi đủ không gian; edge
đang highlight luôn có label. Legend cố định ở toolbar/canvas header.

## Interaction model

### Toolbar

- Search input: `Tìm service, client, database...`
- Layer filter: `Tất cả`, `Client`, `Gateway`, `Service`, `Infrastructure`.
- Status filter: `Đang sử dụng`, `Private`, `Tất cả snapshot`.
- Flow lens: `Không highlight` hoặc một flow canonical.
- Buttons: `Vừa khung`, `Đặt lại bố cục`.

### Node selection

Click node sẽ cập nhật query state hiện có (`node=<id>`) và mở inspector. Inspector
hiển thị:

- Tên kỹ thuật và tên dễ hiểu.
- Vai trò/mô tả.
- Trách nhiệm chính.
- Interface.
- Data owner nếu có.
- Status.
- Các flow liên quan và link nội bộ đến flow/service detail.
- Danh sách connection vào/ra.

### Edge selection

Click edge sẽ cập nhật `connection=<id>` và inspector hiển thị source, target,
kiểu giao tiếp, label và detail. Click node/edge không thay đổi dữ liệu canonical.

### Navigation and highlighting

- Hover một node làm mờ các node/edge không liên quan.
- Hover một edge chỉ nhấn source, target và edge đó.
- Flow lens chỉ highlight các node có trong flow đang chọn và các connection
  canonical liên quan; không ẩn toàn bộ topology.
- Deep link giữ được `step`, `node`, `connection` và flow query nếu đã có.

## Responsive behavior

- Desktop/tablet ngang: React Flow canvas với inspector ở panel bên phải.
- Mobile: không ép canvas rộng gây overflow. Thay bằng accordion theo layer,
  mỗi node là row/card đọc được; connection thành danh sách `source → target`.
- Inspector mobile là bottom sheet hoặc panel phía dưới danh sách.
- Search, filter, legend và status text vẫn hiển thị; không dựa vào hover để
  truy cập thông tin quan trọng.

## Component boundaries

- `ArchitectureView`: orchestration, query state và page-level controls.
- `ArchitectureCanvas`: React Flow provider, nodes/edges, viewport controls,
  minimap và canvas-level interactions.
- `ArchitectureNode`: presentation cho client/edge/service/infrastructure.
- `ArchitectureEdge`: presentation và selection cho từng communication edge.
- `architectureGraph.ts`: chuyển canonical nodes/connections thành graph model,
  lọc theo layer/status/flow và tính highlight.
- `architectureLayout.ts`: chuyển graph model sang ELK input, chạy layout và
  trả positions/fallback.
- `ArchitectureInspector`: chi tiết node/edge và liên kết handbook.
- `ArchitectureMobileList`: responsive fallback không phụ thuộc canvas.

## Accessibility

- Mọi node/edge có accessible name dựa trên label kỹ thuật + vai trò.
- Node và edge có focus state rõ ràng; selection không chỉ thể hiện bằng màu.
- Toolbar controls có label tiếng Việt và keyboard reachable.
- Canvas có vùng mô tả ngắn và legend; thông tin quan trọng phải có bản list
  fallback cho screen reader/mobile.
- Tôn trọng `prefers-reduced-motion`; không dùng animation liên tục để truyền
  trạng thái.

## Validation and acceptance

Focused tests phải chứng minh:

1. Architecture route render đúng heading, legend, canonical node và không yêu
   cầu authentication.
2. Click node mở đúng inspector và query `node`.
3. Click edge mở đúng source/target/detail và query `connection`.
4. Flow lens highlight đúng các node/connection từ canonical data.
5. Mobile fallback hiển thị được layer, node và connection mà không cần canvas.
6. Unknown query không làm trang trắng hoặc phá layout.

Browser QA phải kiểm tra desktop và mobile, gồm page identity, non-blank,
không có framework overlay, console health, screenshot và ít nhất một vòng
click node/edge/filter. Repository checks giữ nguyên `npm run verify:ci`.
