# Agent Instructions

## System Context

Nền tảng giao đồ ăn quy mô lớn, **polyrepo** gồm 4 project (mỗi cái là git repo
riêng; gốc `delivery/` không phải git repo):

| Project | Stack | Vai trò |
|---|---|---|
| `backend_delivery/` | Spring Boot 3.x microservices, Kafka, Redis, PostgreSQL | Toàn bộ nghiệp vụ server. Có harness riêng: `backend_delivery/AGENTS.md` |
| `delivery_app/` | Flutter | App khách hàng (đặt món, tracking, ví voucher) |
| `delivery_web/` | React + Vite + Firebase | Web admin + web nhà hàng (dashboard, quản trị) |
| `shipper_app2/` | React Native (react 19) | App shipper (nhận đơn, giao, thu nhập) |

Đây là **entry point cấp hệ thống**. Context/luồng chéo nhiều project sống ở
đây; chi tiết từng repo nằm trong repo đó.

- Tổng quan hệ thống & bản đồ service: `backend_delivery/docs/platform/product/overview.md`.
- Canonical cross-system docs: `backend_delivery/docs/platform/`.
- Việc chi tiết backend + roadmap MVP→prod: `backend_delivery/ROADMAP_MVP_TO_PRODUCTION.md`.
- Đặc tả backend: `backend_delivery/docs/`.

Khi thay đổi ảnh hưởng nhiều project (ví dụ đổi field một Kafka event hay API
contract), tạo plan trong `docs/plans/active/` ở gốc này. Thay đổi gói gọn trong
một repo thì làm trong repo đó.

<!-- HARNESS:BEGIN -->
## Harness

Start with the requested outcome, then use the repository as the system of
record. Read `docs/WORKFLOW.md` and only the product, design, plan,
code, and validation material relevant to the task.

- Answers, explanations, reviews, diagnoses, plans, and status reports are
  read-only. Inspect only what is needed and do not mutate repository or Harness
  state.
- For a bounded change, use an ephemeral plan: inspect the affected behavior and
  existing proof, implement the change, and run behavior-appropriate validation.
  No Harness CLI operation is required.
- Create or update one file under `docs/plans/active/` when work spans sessions,
  needs coordination or an ordered sequence, has meaningful dependencies, or
  requires explicit recovery steps. Move it to `docs/plans/completed/` only
  after validation.
- Before editing, identify repository authority for each new externally
  observable policy. If materially different choices remain open, stop before
  edits; configurable defaults are not authority.
- Also pause when product intent remains ambiguous, an action is difficult to
  recover, validation would be weakened, or the request does not authorize the
  needed action.
- Claim completion only with relevant executable or observable evidence. Report
  the outcome, important changed surfaces, validation, and unresolved risks.

SQLite intake, story, trace, scoring, audit, and proposal commands are optional
compatibility features. Use them only when explicitly requested or required by
an external orchestrator.
<!-- HARNESS:END -->
