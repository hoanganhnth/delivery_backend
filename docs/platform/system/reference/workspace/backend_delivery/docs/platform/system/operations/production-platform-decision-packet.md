# Phiếu Chốt Nền Tảng Production

> Trạng thái: **Decision required**. Không trường nào dưới đây được ngầm coi là
> đã duyệt chỉ vì có template Kubernetes hoặc local Compose. Điền/duyệt phiếu
> này là điều kiện để tạo overlay thật và rollout staging/production.
> Xem thứ tự tiếp tục đã được ghi lại tại
> [deferred implementation handoff](../../plans/active/system-reconstruction-and-production-foundation.md#deferred-implementation-handoff).

## Cách dùng

Chủ hệ thống chỉ cần trả lời theo bảng “Quyết định cần chốt”. Nếu chưa chọn,
ghi `chưa chọn`; agent/infra owner sẽ đưa 2–3 option có chi phí/rủi ro trước khi
tạo hạ tầng. Không ghi domain thật, token, password, private key hoặc connection
string vào file này.

## Quyết định cần chốt

| ID | Quyết định | Lựa chọn / giá trị cần trả lời | Vì sao cần trước khi triển khai |
| --- | --- | --- | --- |
| P1 | Nền tảng cluster | AWS / GCP / Azure / on-prem; region(s); account/project/subscription owner | Quyết định network, workload identity, registry, storage, vùng DR và cost control |
| P2 | K8s ownership | Managed Kubernetes hay tự vận hành; ai on-call cluster/control plane | Quyết định upgrade, node autoscaling, ingress, policy và incident ownership |
| P3 | Data plane | PostgreSQL, Kafka, Redis, search: managed hay self-hosted; owner mỗi dịch vụ | Không được chạy single-node Compose data plane như production |
| P4 | Secrets | Vault / cloud secret manager / External Secrets / cơ chế on-prem; rotation/audit owner | Cần cấp JWT/DB/Kafka/SMTP/FCM qua workload identity, không commit secret |
| P5 | Public edge | Domain, DNS owner, TLS/cert manager, WAF/CDN/Ingress provider, allowed client origins | Chỉ Gateway là ingress; quyết định này định nghĩa proxy CIDR và CORS an toàn |
| P6 | Observability | Prometheus/Grafana/Alertmanager và log/trace backend; retention, access, on-call channel | Local OTel debug exporter không đủ cho production incident/release gate |
| P7 | Resilience target | RPO/RTO cho COD/identity, backup region, data-retention/compliance owner | Cần chốt PITR/WAL, DR drill, Kafka retention và rollback strategy |
| P8 | Capacity/SLO | Traffic forecast, peak order/sec, active sockets/shipper, p95/error/lag/SLO budget, cost ceiling | Quyết định replicas, HPA/PDB, JVM resources, canary threshold và load plan |
| P9 | Documentation ownership | Root `delivery/` hiện không phải Git repository; chọn repo/parent nào version-control `docs/system/` và reference bundle | Nếu không chốt, tài liệu tái tạo không có lịch sử/rollback/CI authority dù file hiện đang tồn tại trên máy |

## Mẫu trả lời tối thiểu

```text
P1 platform/region: ...
P2 Kubernetes ownership: ...
P3 data plane: PostgreSQL=..., Kafka=..., Redis=..., Search=...
P4 secrets: ...
P5 edge: DNS/TLS/WAF/Ingress=..., allowed origins=...
P6 observability: ...
P7 RPO/RTO + retention: ...
P8 traffic/SLO/cost: ...
P9 documentation repository/owner: ...
```

## Hướng khuyến nghị khi chưa có preference

Đây là **khuyến nghị để thảo luận**, không phải quyết định đã áp dụng:

1. Dùng managed Kubernetes ở một region staging trước, tách account/project
   khỏi production; giữ application workloads private và chỉ có Gateway đi qua
   managed Ingress/WAF.
2. Dùng managed PostgreSQL có PITR, managed Kafka có replication/ACL/DLT
   retention và managed Redis; không tự vận hành ba stateful system này cùng
   lúc nếu chưa có SRE/on-call chuyên trách.
3. Dùng workload identity + external secret manager; ExternalSecret chỉ là
   delivery object trong cluster, không phải source of truth.
4. Bắt đầu staging với Grafana/Prometheus/Alertmanager và một trace/log backend
   có retention nhỏ nhưng có alert/on-call thật; load-test trước khi đặt HPA.
5. Không chọn production traffic/cost/SLO theo cảm tính. Chạy COD/realtime load
   scenario rồi chốt resource/replica/canary stop condition bằng số đo.

## Sau khi P1–P9 được duyệt

1. Tạo private environment overlay từ
   [`backend_delivery/deploy/kubernetes/`](../../../../deploy/kubernetes),
   thay mọi `example.invalid` và immutable image digest; không commit secret.
2. Tạo data-plane provisioning, database roles, Kafka topics/ACL/DLT retention,
   backup/PITR, service accounts và external-secret references theo P1–P4.
3. Tạo Gateway-only Ingress/TLS/WAF, NetworkPolicy egress theo private CIDR/DNS,
   CORS/trusted-proxy CIDR theo P5.
4. Provision metrics/logs/traces/alerts và release dashboard theo P6; chốt
   resource/PDB/HPA/canary policy theo P7–P8.
5. Rollout Auth → resource services → Gateway; run JWKS/COD smoke, DLT/restart,
   backup-restore and rollback rehearsals before traffic promotion.

## Authority and evidence links

- [Kubernetes foundation README](../../../../deploy/kubernetes/README.md)
- [Deployment foundation](./deployment-foundation.md)
- [Release/recovery procedure](./release-and-recovery.md)
- [Secrets runbook](../../../runbooks/secrets-management.md)
- [Backup/restore runbook](../../../runbooks/data-backup-restore.md)
