# Search Service

## Phạm vi MVP

`search-service` cung cấp full-text search cho nhà hàng và món ăn qua
Elasticsearch. Service nhận projection event `entity-sync` từ Kafka; không sở
hữu dữ liệu nghiệp vụ gốc.

- Public qua Gateway: `GET /api/search/restaurants`,
  `GET /api/search/dishes`.
- Shipper search/index không thuộc MVP và graph đã xóa; admin fleet đọc
  `shipper-service`, còn matching dùng Redis GEO.
- Redis không thuộc Search graph. Với quy mô MVP, Elasticsearch là read store
  duy nhất; không duy trì cache kết quả có invalidation thiếu chắc chắn.

## Luồng projection

1. Restaurant/Menu mutation commit dữ liệu gốc và outbox `entity-sync` trong
   cùng transaction.
2. Outbox relay publish event có stable UUID, `occurredAt`, entity/action/id và
   payload.
3. Search validate metadata/action/entity trước mọi mutation.
4. Search atomically claim checkpoint theo entity bằng Elasticsearch scripted
   upsert, gồm event ID, thời điểm, action và SHA-256 fingerprint của payload.
   Claim cũ hơn là no-op; cùng ID chỉ được replay khi metadata/payload khớp.
   Checkpoint legacy thiếu fingerprint chỉ được nâng cấp bằng compare-and-set
   `_seq_no`/`_primary_term` sau khi metadata đã được chuẩn hoá/kiểm tra (cả
   legacy timestamp có giây rỗng hoặc UTC offset).
5. Search upsert/delete document bằng `version_type=external_gte`, version là
   `occurredAt` đến nanosecond. Vì vậy nếu hai partition/replica đã cùng claim
   các version khác nhau rồi write theo thứ tự đảo, update cũ không thể ghi đè
   update mới hoặc hồi sinh document đã DELETE.
6. Exact retry được phép chạy lại document mutation để phục hồi crash sau
   checkpoint; event cũ và replay cùng ID nhưng metadata/payload mâu thuẫn bị
   fail-closed.

Khi Elasticsearch lỗi, consumer ném lỗi để Kafka retry/DLT; không ACK hoặc bỏ
qua projection im lặng. Rehearsal Testcontainers đã cover hai Search replica,
hai partition reorder, exact same/fresh-group replay, contradictory ID tới DLT
và delayed old writer sau update/delete mới. Search vẫn là rebuildable
projection: Elasticsearch cluster outage/index recreation và vận hành replay có
kiểm soát vẫn là Gate B8 chưa hoàn tất.

## Luồng query

Request phải có query không blank, page không âm và size tối đa 100. Service đọc
trực tiếp Elasticsearch và trả `BaseResponse` có page DTO ổn định
`{items,page,size,totalItems,totalPages,hasNext}`; không serialize trực tiếp
Spring `Page`. Khi Elasticsearch/repository không sẵn sàng hoặc query thất bại,
API trả HTTP `503` với `status=0`, `data=null` và message đã làm sạch; không trả
`200 []` gây hiểu nhầm rằng không có kết quả.

## Trạng thái

Backend restaurant/dish API, validation, response contract và projection safety
có focused proof. Flutter parser chỉ nhận canonical `BaseResponse` + stable page
và reject raw Spring Page. Runtime Elasticsearch cluster recovery chưa được coi
là hoàn tất. Search HTTP chỉ còn restaurant/dish public contract.
