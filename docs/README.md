# 📚 Delivery Platform Documentation

Hệ thống tài liệu kiến trúc tổng thể cho dự án Delivery. Tài liệu được chia thành 2 nhóm chính để dễ quản lý và theo dõi.

## System-level architecture

- [Editable Mermaid architecture](../../docs/ARCHITECTURE.md): polyrepo context,
  client boundary, JWKS, core workflows, data ownership and operations mechanisms.
- [Backend product overview](product/overview.md): current service map and MVP
  capability boundary.
- [System contract inventory](system-contract-inventory.md): executable inventory
  for persistence, Gateway surfaces, Kafka contracts and remaining runtime proof.

## 📍 1. Independent Services
Tài liệu đặc tả các dịch vụ độc lập, quản lý domain nội bộ của nó. (APIs, CRUD, Logic nội bộ)

- [Auth & User Management](services/auth_and_users.md)
- [Restaurant & Menu Management](services/restaurant_and_menu.md)
- [Search Service (Elasticsearch)](services/search_service.md)
- [Analytics & Dashboard](services/analytics_dashboard.md)
- [Livestream Management](services/livestream_management.md)
- [Notification Service (FCM & In-app)](services/notification_service.md)
- [Realtime Tracking Service (WebSocket)](services/tracking_service.md)

## 📍 2. Cross-Service Workflows
Tài liệu đặc tả các luồng nghiệp vụ phức tạp, có sự giao tiếp chéo giữa nhiều microservices, Kafka, và database (Saga, Integration).

- [Order Lifecycle Flow](workflows/order_lifecycle_flow.md)
- [Flash Sale Flow](workflows/flash_sale_flow.md)
- [Promotion & Voucher Flow](workflows/promotion_voucher_flow.md)
- [Delivery Matching & Tracking](workflows/delivery_matching_tracking.md)
- [Settlement & Finance Flow](workflows/settlement_finance_flow.md)

## 📍 Operations

- [Health, liveness and readiness](operations/health-readiness.md)
- [Service discovery and naming](operations/service-discovery.md)
- [Centralized configuration](operations/centralized-configuration.md)
- [Hot query and index audit](operations/hot-query-audit.md)
- [Raw WebSocket scaling](operations/websocket-scaling.md)
- [Shipper location history](operations/location-history.md)
- [Data backup and restore](runbooks/data-backup-restore.md)
- [Account recovery and email verification](runbooks/account-recovery-email-verification.md)
- [Secrets management and rotation](runbooks/secrets-management.md)
- [Rolling/canary rollout and rollback](runbooks/rollout-and-rollback.md)
- [JWKS Compose rollout runner](runbooks/rollout-and-rollback.md#jwks-migration-on-localstaging-compose)
- [JWKS legacy Compose bootstrap](../scripts/bootstrap-jwks-legacy-compose.sh)
- [Refund and cancellation compensation](runbooks/refund-workflow.md)
