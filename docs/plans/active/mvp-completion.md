# Execution Plan: Hoàn thiện MVP giao hàng

Date: 2026-07-21

## Status

Active

> Từ 2026-07-22, plan này chỉ giữ context backend cũ. Nguồn tiến độ và thứ tự
> triển khai chính là `../../../../docs/plans/active/priority-roadmap.md` ở workspace
> root. Không mở implementation song song từ checklist bên dưới; các mục sẽ được
> kiểm chứng lại trong từng backend wave của master plan. gRPC tracking không còn
> thuộc MVP hiện tại.

## Outcome

Luồng đặt–giao–đối soát chạy trọn vẹn và ổn định dưới tải thật: không có đơn
treo ở `FINDING_SHIPPER`, shipper có thể bỏ đơn và hệ thống tự rematch, và không
có endpoint/service nào sập vì `findAll()` hay thread bị block.

## Context

- `ROADMAP_MVP_TO_PRODUCTION.md` §1 — nguồn việc và mức ưu tiên.
- `docs/product/overview.md` — service map & bất biến.
- `docs/workflows/delivery_matching_tracking.md`, `settlement_finance_flow.md`.

## Scope

In scope:

- Gắn `DeliveryWaitingService` vào luồng chính (auto-retry Redis TTL).
- Shipper hủy sau accept → reset `FINDING_SHIPPER` + phát
  `delivery.shipper-rejected`; Saga rematch bằng `saga.command.find-shipper`.
- Raw WebSocket là transport tracking MVP; gRPC đã bị loại khỏi scope/dependency.
- `ShipperService.findAll()` → `Pageable`.
- Match retry: bỏ block Kafka consumer thread (delayed topic / scheduler).
- Đổi default secret trong `application.properties` (fail-fast nếu thiếu env).
- Order status `String` → `Enum`.

Out of scope:

- Observability, rate-limit, circuit-breaker, DLQ, Eureka — thuộc giai đoạn
  production (`ROADMAP_MVP_TO_PRODUCTION.md` §2).

## Approach

Xử lý theo nhóm của roadmap §1: khép kín luồng giao hàng (1.1) trước, rồi chống
sập dưới tải (1.2), rồi an toàn dữ liệu (1.3), cuối cùng vệ sinh code (1.4). Mỗi
mục kèm proof tương ứng trước khi tick.

## Risks And Recovery

- Đổi default secret có thể làm service không boot nếu env chưa set → cung cấp
  `.env.example` và kiểm tra ở môi trường staging trước.
- Chuyển match retry sang scheduler có thể đổi thứ tự xử lý → giữ idempotency key.
- Rollback: mỗi thay đổi là một commit độc lập, revert được riêng.

## Progress

- [x] Loại DeliveryWaitingService dead path; Saga sở hữu timeout/rematch
- [x] Shipper cancel-after-accept → rematch
- [x] Loại gRPC khỏi MVP và giữ raw WebSocket
- [x] ShipperService pagination
- [x] Match retry không block Kafka thread
- [x] Đổi default secret + fail-fast
- [x] Order status Enum

## Decisions

- 2026-07-21: Tách production-grade (observability/resilience) ra khỏi plan này
  để MVP không bị chặn bởi hạ tầng vận hành.

## Validation

- Focused proof: unit test cho waiting-service integration, rematch, status enum.
- Integration proof: e2e đặt hàng → không tìm được shipper → auto-retry → match.
- Repository-required checks: build Maven toàn bộ module + test hiện có pass.

## Result

Điền sau khi hoàn thành và validate, rồi chuyển sang `docs/plans/completed/`.
</content>
