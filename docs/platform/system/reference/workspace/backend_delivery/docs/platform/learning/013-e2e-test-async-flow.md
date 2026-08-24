# 013 — Test end-to-end luồng async (đặt→giao)

> Ngày: 2026-07-22 · Service: nhiều (qua REST API)
> Liên quan: [TESTING_READINESS](../TESTING_READINESS.md), `scripts/test-order-flow.sh`

> **Superseded:** `scripts/test-order-flow.sh` hiện chỉ là compatibility wrapper
> sang `scripts/verify-mvp-cod-flow.sh`. Mô tả polling Delivery/settlement soft
> verify bên dưới là lịch sử, không còn là acceptance contract.

## Mình đã làm gì
Viết `scripts/test-order-flow.sh`: chạy trọn luồng đặt hàng → giao hàng → đối soát
qua REST, kiểm tra ✅/❌ từng bước. Bổ sung cho `seed.sh` (chỉ dựng dữ liệu).

## Kỹ thuật quan trọng

### 1. Test hệ event-driven ⇒ poll đúng observable contract, không assert ngay
Luồng đi qua Kafka + Saga: đặt đơn `POST /orders` trả về **ngay**, nhưng delivery
được Saga tạo **bất đồng bộ** sau đó. Nếu assert delivery tồn tại ngay sau khi đặt →
fail giả. Harness hiện poll durable Notification inbox rồi recover exact self-offer;
không dùng customer Delivery read làm bằng chứng shipper đã nhận offer. Đây là bản
chất test hệ async — khác test đồng bộ (gọi xong assert luôn).

### 2. Biết chỗ nào đồng bộ, chỗ nào bất đồng bộ
- Đồng bộ (assert ngay): login, tạo nhà hàng/menu, accept (trả DeliveryResponse).
- Bất đồng bộ (poll/sleep): tạo delivery (Saga), settlement cộng tiền (Kafka
  DeliveryCompleted). Đặt `sleep` ngắn sau các bước async rồi mới đọc.
Vẽ được ranh giới này thì test vừa nhanh vừa không flaky.

### 3. Test phản ánh đúng luồng nghiệp vụ mới
Sau khi bật gate confirm (#009), test PHẢI có bước nhà hàng confirm giữa "đặt đơn"
và "shipper accept" — nếu không đơn đứng. Test tốt là test đi **đúng luồng thật**,
kể cả các cổng điều kiện mới thêm.

### 4. Lấy id từ response thay vì đoán
`shipperId` để tra settlement được lấy từ **response của accept** (`.data.shipperId`),
không hardcode. Giảm giả định, chạy đúng với dữ liệu thực tế của lần chạy đó.

## Quyết định & đánh đổi
- **Settlement fail-fast:** acceptance harness bắt buộc đúng bốn ledger entries
  và exact replay không đổi cardinality; không còn cảnh báo mềm che lỗi.
- **Không mock**: test chạy thật qua gateway → bắt được lỗi tích hợp thật (điều mà
  unit test bỏ sót). Đổi lại cần cụm docker chạy.

## Cạm bẫy / lỗi dễ mắc
- **Không idempotent hoàn toàn**: guard "1 shipper 1 đơn active" → nếu lần trước fail
  giữa chừng, shipper kẹt đơn cũ, lần sau accept lỗi. Đã ghi cách xử lý trong doc.
- Poll không có giới hạn → treo vô hạn nếu service chết. Luôn đặt max attempts + báo lỗi rõ.
- Canonical harness dùng `set -euo pipefail`, `curl --fail-with-body` và timeout
  hữu hạn để lỗi HTTP/async không bị nuốt.

## Cách kiểm chứng
- `bash -n` (syntax) đã pass.
- Chạy thật khi có cụm: in ra ✅ từng bước tới "DELIVERED" + số dư settlement.

## Câu hỏi mở
- Thêm case biên: shipper reject → rematch; nhà hàng reject → huỷ; timeout confirm.
- Dọn dữ liệu sau test (teardown) để chạy lặp sạch.

## Muốn đào sâu thêm
Từ khoá: "testing event-driven systems polling", "async integration test eventual
consistency", "idempotent test data setup", "bash set -e pitfalls curl".
