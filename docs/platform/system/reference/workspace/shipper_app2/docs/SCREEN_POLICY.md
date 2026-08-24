# Shipper MVP Screen Policy

Screen production phải lấy dữ liệu từ Gateway/backend hoặc hiển thị trạng thái
thiếu dữ liệu. Không dùng identity, earnings, tier, số đơn, notification count,
customer hoặc GPS route giả làm fallback.

## Screen được giữ

- Login bằng password hoặc Google khi đã cấu hình provider.
- Main map dùng native GPS, raw Tracking WebSocket qua Gateway và delivery data
  canonical.
- Current offer popup/recovery, accept/reject và cancel-assignment.
- Delivery lifecycle `ASSIGNED → PICKED_UP → DELIVERING → DELIVERED`.
- Bounded delivery history/detail.
- Durable notifications, profile, ratings, documents và settings phù hợp API.

## Screen/capability bị ẩn

- Shipper self-registration cho tới khi có atomic/recoverable onboarding từ Auth
  sang Shipper profile.
- Forgot/change password khi backend chưa có contract.
- Settlement balance, payout, withdrawal, tip, incentive và weekly tier.
- Fake GPS route, STOMP notification và direct service-port access.

Mọi màn mới phải có route/API authority, typed adapter, focused contract test và
runtime proof tương ứng trước khi thêm vào navigation production.
