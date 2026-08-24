# Feature: <Tên chức năng>

> Trạng thái tài liệu: 🟢 verified từ code | 🟡 nháp cần review | 🔴 chưa xác minh
> Service liên quan: <service> · Repo: <repo> · Cập nhật: YYYY-MM-DD

## 1. Mục đích
Chức năng này giải quyết nhu cầu gì, cho ai.

## 2. Actor & quyền
| Actor | Được làm gì |
|---|---|
| CUSTOMER | ... |
| RESTAURANT_OWNER | ... |
| SHIPPER | ... |
| ADMIN | ... |

## 3. Điểm vào (entry points)
| Loại | Định danh | Ghi chú |
|---|---|---|
| REST | `METHOD /path` | ... |
| Kafka consume | `topic` | ... |
| Kafka publish | `event` | ... |

## 4. Tiền điều kiện
Điều kiện phải đúng trước khi chạy (dữ liệu tồn tại, trạng thái hợp lệ...).

## 5. Luồng chính (happy path)
Các bước theo thứ tự, kèm side-effect (ghi DB, bắn event, gọi service khác).

## 6. Các case & nhánh rẽ
Liệt kê **mọi** nhánh: điều kiện → hành vi → kết quả. Đây là phần quan trọng nhất.

| # | Điều kiện | Hành vi | Kết quả |
|---|---|---|---|
| C1 | ... | ... | ... |

## 7. Quy tắc nghiệp vụ (business rules)
Các ràng buộc bất biến (ai được hủy khi nào, tính phí thế nào...).

## 8. Case lỗi & ngoại lệ
| Tình huống | Xử lý hiện tại | Mã lỗi/exception |
|---|---|---|

## 9. Trạng thái & dữ liệu
Entity/field liên quan, chuyển trạng thái.

## 10. Phụ thuộc
Service/hệ thống khác mà chức năng này gọi hoặc phụ thuộc.

## 11. Khoảng trống / rủi ro đã biết
Điều chưa đúng hoặc chưa có (trỏ về Roadmap nếu cần).

## 12. Câu hỏi mở cho review
Chỗ cần chủ dự án xác nhận ý định nghiệp vụ.
