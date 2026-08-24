# Db Development Instructions - DeliveryVN Microservices 
// === AUTH SERVICE ===
Table AuthAccount {
  id bigint [pk]
  email varchar(255) [unique]
  password_hash varchar(255)
  role varchar(20)
  is_active boolean
  created_at timestamp
  updated_at timestamp
}
// === AUTH SERVICE ===
Table AuthSession {
  id bigint [pk]
  auth_id bigint [ref: > AuthAccount.id]
  device_id varchar(255)         // UUID sinh từ client
  device_name varchar(255)       // Ví dụ: "iPhone 14", "Chrome on Windows"
  device_type enum("mobile", "web", "tablet")
  ip_address varchar(50)
  refresh_token text             // Gắn với phiên login
  is_active boolean              // Đã đăng xuất chưa
  last_login_at timestamp
  expires_at timestamp
  created_at timestamp
}

// === USER SERVICE ===
Table User {
  id bigint [pk]
  auth_id bigint [ref: > AuthAccount.id]
  full_name varchar(100)
  phone varchar(20)
  dob date
  avatar_url text
  address text
  created_at timestamp
  updated_at timestamp
}
// === USER SERVICE ===
Table UserAddress {
  id bigint [pk]
  user_id bigint [ref: > User.id]
  label varchar(100)               // VD: "Nhà", "Cơ quan"
  address_line text
  ward varchar(100)
  district varchar(100)
  city varchar(100)
  postal_code varchar(20)
  latitude double
  longitude double
  is_default boolean               // Có phải là địa chỉ mặc định không?
  created_at timestamp
  updated_at timestamp
}
// === SHIPPER SERVICE ===
Table Shipper {
  id bigint [pk]
  user_id bigint [ref: > User.id]
  vehicle_type varchar(50)
  license_number varchar(50)
  id_card varchar(20)
  driver_image text
  is_online boolean
  rating decimal(2,1)
  completed_deliveries int
  created_at timestamp
  updated_at timestamp
}
// === SHIPPER SERVICE ===
Table shipper_balances {
  id bigint [pk, increment]
  shipper_id bigint [ref: > Shipper.id, unique]
  balance decimal(12,2)          // Số tiền hiện tại có thể dùng
  holding_balance decimal(12,2)  // Số tiền giữ tạm khi nhận đơn
  updated_at timestamp
}
// === SHIPPER SERVICE ===
Table shipper_locations {
  id bigint [pk, increment]
  shipper_id bigint [ref: > Shipper.id, unique]
  lat double
  lng double
  updated_at timestamp
}
// === SHIPPER SERVICE ===
Table shipper_transactions {
  id bigint [pk, increment]
  shipper_id bigint [ref: > Shipper.id]
  related_order_id bigint [ref: > Order.id, null]
  transaction_type enum('deposit','withdraw','earn','penalty','hold','release')
  amount decimal(12,2)
  description text
  created_at timestamp
}
// === RESTAURANT SERVICE ===
Table Restaurant {
  id bigint [pk]
  name text
  address text
  phone varchar(20)
  created_at timestamp
  updated_at timestamp
  creator_id bigint [ref: > User.id]
  opening_hour  time        // 🕘 giờ mở cửa (ví dụ: 09:00)
  closing_hour  time   
   status         varchar(20)   [default: 'AVAILABLE'] // OPEN, CLOSED, TEMPORARILY_CLOSED
}

// === RESTAURANT SERVICE ===
Table MenuItem {
  id             bigint        [pk]
  restaurant_id  bigint        [ref: > Restaurant.id]
  name           varchar(255)
  description    text
  price          decimal(12,2)
  status         varchar(20)   [default: 'AVAILABLE'] // AVAILABLE, SOLD_OUT, DISCONTINUED
  created_at     timestamp     [default: `now()`]
  updated_at     timestamp     [default: `now()`]
}
// === RESTAURANT SERVICE ===
Table restaurant_balances {
  id bigint [pk, increment]
  restaurant_id bigint [ref: > Restaurant.id, unique]
  available_balance decimal(12,2) // Số dư có thể rút
  pending_balance decimal(12,2)   // Doanh thu chờ thanh toán
  total_earnings decimal(12,2)
   created_at timestamp
  updated_at timestamp
}
// === RESTAURANT SERVICE ===
Table restaurant_transactions {
  id bigint [pk, increment]
  restaurant_id bigint [ref: > Restaurant.id]
  order_id bigint [ref: > Order.id, null]
  type enum('earning', 'commission', 'withdraw', 'adjustment', 'refund')
  amount decimal(12,2)
  description text
  created_at timestamp
   updated_at timestamp
}

// === ORDER SERVICE ===
Table Order {
  id bigint [pk]
  user_id bigint [ref: > User.id]
  restaurant_id bigint [ref: > Restaurant.id]
  shipper_id bigint [ref: > Shipper.id]
   subtotal_price decimal(12,2)      //tổng món chưa giảm
  discount_amount decimal(12,2)       // giảm giá
  shipping_fee decimal(12,2)           // phí vận chuyển
  total_price decimal(12,2)            //-- tổng tiền cuối cùng
  created_at timestamp
  updated_at timestamp
}

// === ORDER SERVICE ===
Table OrderItem {
  id bigint [pk]
  order_id bigint [ref: > Order.id]
  menu_item_id bigint [ref: > MenuItem.id]
  quantity int
  price decimal(12,2)
}

// === DELIVERY SERVICE ===
Table Delivery {
  id bigint [pk]
  order_id bigint [ref: > Order.id]
  shipper_id bigint [ref: > Shipper.id]
  start_time timestamp
  end_time timestamp
  current_lat double
  current_lng double
  updated_at timestamp
}

// ===== promotion-service =====

Table Promotion {
  id bigint [pk]
  code varchar(50) [unique]              // Mã giảm giá: "SALE20", "FOOD5K"
  title varchar(255)
  description text
  type enum("PERCENT", "FIXED", "FREE_ITEM") // Kiểu giảm giá
  discount_value decimal(12,2)           // 10% hoặc 20,000đ
  max_discount_value decimal(12,2)       // Giảm tối đa (cho %)
  min_order_value decimal(12,2)
  start_time timestamp
  end_time timestamp
  max_uses int                           // Tổng lượt sử dụng toàn hệ thống
  limit_per_user int     
  is_active boolean           // ✅ true = mã đang mở, false = mã đã tắt
  deactivated_reason text     // (tuỳ chọn) lý do bị tắt, ví dụ "Lạm dụng"               // Số lần dùng tối đa trên mỗi người dùng
  created_by enum("ADMIN", "RESTAURANT")// Người tạo
  creator_id bigint                      // Nếu là quán → restaurant_id
  created_at timestamp
}

// Áp dụng cho quán nào
Table PromotionRestaurant {
  id bigint [pk]
  promotion_id bigint [ref: > Promotion.id]
  restaurant_id bigint [ref: > Restaurant.id]
}

// Áp dụng cho món nào
Table PromotionMenuItem {
  id bigint [pk]
  promotion_id bigint [ref: > Promotion.id]
  menu_item_id bigint [ref: > MenuItem.id]
}

// Theo dõi lượt sử dụng
Table PromotionUsage {
  id bigint [pk]
  promotion_id bigint [ref: > Promotion.id]
  user_id bigint [ref: > User.id]
  order_id bigint [ref: > Order.id]
  used_at timestamp
}

// ❌ Những dòng dưới bị sai logic, nên bạn nên xoá:
Ref: "Restaurant"."id" < "Restaurant"."address"
Ref: "Delivery"."end_time" < "Delivery"."current_lat"
