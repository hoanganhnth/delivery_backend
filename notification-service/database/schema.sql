-- Notification Service Database Schema
-- PostgreSQL Database: notification_service_db

-- Create database (run as postgres user)
-- CREATE DATABASE notification_service_db;
-- \c notification_service_db;

-- Create notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    priority VARCHAR(20) DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED', 'READ')),
    is_read BOOLEAN DEFAULT FALSE,
    related_entity_id BIGINT,
    related_entity_type VARCHAR(50),
    data TEXT, -- JSON data for additional info
    sent_at TIMESTAMP,
    read_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    creator_id BIGINT
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_status ON notifications(user_id, status);
CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON notifications(user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_type ON notifications(type);
CREATE INDEX IF NOT EXISTS idx_notifications_priority ON notifications(priority);
CREATE INDEX IF NOT EXISTS idx_notifications_related_entity ON notifications(related_entity_type, related_entity_id);

-- Create composite index for common queries
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications(user_id, is_read, created_at DESC) WHERE is_read = FALSE;

-- Create function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger to call the function
DROP TRIGGER IF EXISTS update_notifications_updated_at ON notifications;
CREATE TRIGGER update_notifications_updated_at
    BEFORE UPDATE ON notifications
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Insert sample data for testing
INSERT INTO notifications (user_id, title, message, type, priority, status, is_read, related_entity_id, related_entity_type, data, creator_id) VALUES
-- Order notifications
(123, 'Đơn hàng đã được tạo', 'Đơn hàng #456 từ Nhà hàng ABC đã được tạo thành công với tổng tiền 150.000 VND', 'ORDER_CREATED', 'MEDIUM', 'SENT', FALSE, 456, 'ORDER', '{"orderId": 456, "restaurantName": "Nhà hàng ABC", "totalAmount": 150000}', 1),
(123, 'Đơn hàng được xác nhận', 'Nhà hàng ABC đã xác nhận đơn hàng #456 và đang chuẩn bị', 'ORDER_CONFIRMED', 'LOW', 'SENT', FALSE, 456, 'ORDER', '{"orderId": 456, "restaurantName": "Nhà hàng ABC", "estimatedTime": 30}', 1),
(123, 'Đơn hàng đang chuẩn bị', 'Nhà hàng ABC đang chuẩn bị đơn hàng #456 của bạn', 'ORDER_PREPARING', 'LOW', 'SENT', TRUE, 456, 'ORDER', '{"orderId": 456, "restaurantName": "Nhà hàng ABC"}', 1),

-- Delivery notifications
(123, 'Tài xế đã được phân công', 'Tài xế Nguyễn Văn A (0901234567) sẽ giao đơn hàng #456 cho bạn', 'SHIPPER_ASSIGNED', 'HIGH', 'SENT', FALSE, 789, 'DELIVERY', '{"deliveryId": 789, "shipperName": "Nguyễn Văn A", "shipperPhone": "0901234567", "orderId": 456}', 1),
(123, 'Đang giao hàng', 'Tài xế Nguyễn Văn A đang trên đường giao đơn hàng #456 đến bạn', 'DELIVERY_STARTED', 'HIGH', 'SENT', FALSE, 789, 'DELIVERY', '{"deliveryId": 789, "shipperName": "Nguyễn Văn A", "orderId": 456, "estimatedArrival": "15 phút"}', 1),

-- System notifications
(123, 'Khuyến mãi đặc biệt', 'Giảm 20% cho đơn hàng tiếp theo! Áp dụng mã SAVE20', 'PROMOTION', 'MEDIUM', 'SENT', FALSE, NULL, NULL, '{"promoCode": "SAVE20", "discount": 20, "validUntil": "2025-01-31"}', 1),

-- Different users for testing
(456, 'Chào mừng đến với DeliveryVN', 'Cảm ơn bạn đã đăng ký tài khoản. Hãy khám phá các nhà hàng ngon quanh bạn!', 'WELCOME', 'LOW', 'SENT', FALSE, NULL, NULL, '{"newUser": true}', 1),
(456, 'Đơn hàng đã được giao', 'Đơn hàng #789 đã được giao thành công. Cảm ơn bạn đã sử dụng dịch vụ!', 'ORDER_DELIVERED', 'HIGH', 'SENT', TRUE, 789, 'ORDER', '{"orderId": 789, "rating": true}', 1),

(789, 'Thông báo bảo trì hệ thống', 'Hệ thống sẽ bảo trì từ 2:00 - 4:00 sáng ngày mai. Vui lòng sắp xếp đơn hàng phù hợp.', 'SYSTEM_MAINTENANCE', 'HIGH', 'SENT', FALSE, NULL, NULL, '{"maintenanceStart": "2025-01-15T02:00:00", "maintenanceEnd": "2025-01-15T04:00:00"}', 1);

-- Create views for common queries
CREATE OR REPLACE VIEW user_unread_notifications AS
SELECT 
    user_id,
    COUNT(*) as unread_count,
    MAX(created_at) as latest_notification
FROM notifications 
WHERE is_read = FALSE 
GROUP BY user_id;

CREATE OR REPLACE VIEW notification_summary AS
SELECT 
    id,
    user_id,
    title,
    LEFT(message, 100) || CASE WHEN LENGTH(message) > 100 THEN '...' ELSE '' END as short_message,
    type,
    priority,
    status,
    is_read,
    created_at,
    CASE 
        WHEN created_at > NOW() - INTERVAL '1 hour' THEN 'just_now'
        WHEN created_at > NOW() - INTERVAL '1 day' THEN 'today'
        WHEN created_at > NOW() - INTERVAL '7 days' THEN 'this_week'
        ELSE 'older'
    END as time_category
FROM notifications
ORDER BY created_at DESC;

-- Function to get user notification stats
CREATE OR REPLACE FUNCTION get_user_notification_stats(p_user_id BIGINT)
RETURNS TABLE(
    total_notifications BIGINT,
    unread_count BIGINT,
    read_count BIGINT,
    high_priority_unread BIGINT,
    latest_notification TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        COUNT(*)::BIGINT as total_notifications,
        COUNT(*) FILTER (WHERE is_read = FALSE)::BIGINT as unread_count,
        COUNT(*) FILTER (WHERE is_read = TRUE)::BIGINT as read_count,
        COUNT(*) FILTER (WHERE is_read = FALSE AND priority IN ('HIGH', 'URGENT'))::BIGINT as high_priority_unread,
        MAX(created_at) as latest_notification
    FROM notifications 
    WHERE user_id = p_user_id;
END;
$$ LANGUAGE plpgsql;

-- Function to clean up old read notifications (data retention)
CREATE OR REPLACE FUNCTION cleanup_old_notifications(days_to_keep INTEGER DEFAULT 90)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM notifications 
    WHERE is_read = TRUE 
      AND read_at < NOW() - INTERVAL '1 day' * days_to_keep;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Create procedure to mark all notifications as read for a user
CREATE OR REPLACE FUNCTION mark_all_user_notifications_read(p_user_id BIGINT)
RETURNS INTEGER AS $$
DECLARE
    updated_count INTEGER;
BEGIN
    UPDATE notifications 
    SET is_read = TRUE, 
        read_at = CURRENT_TIMESTAMP,
        status = CASE WHEN status = 'SENT' THEN 'READ' ELSE status END
    WHERE user_id = p_user_id 
      AND is_read = FALSE;
    
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    
    RETURN updated_count;
END;
$$ LANGUAGE plpgsql;

-- Grant permissions (adjust as needed for your user)
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO notification_service_user;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO notification_service_user;
-- GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO notification_service_user;

-- Example queries for testing:

-- Get all unread notifications for user
-- SELECT * FROM notifications WHERE user_id = 123 AND is_read = FALSE ORDER BY created_at DESC;

-- Get notification stats for user
-- SELECT * FROM get_user_notification_stats(123);

-- Mark all notifications as read for user
-- SELECT mark_all_user_notifications_read(123);

-- Get notification summary
-- SELECT * FROM notification_summary WHERE user_id = 123 LIMIT 10;

-- Clean up old notifications (older than 90 days and read)
-- SELECT cleanup_old_notifications(90);

-- Performance test query
-- EXPLAIN ANALYZE SELECT * FROM notifications WHERE user_id = 123 AND is_read = FALSE ORDER BY created_at DESC LIMIT 20;

COMMIT;
