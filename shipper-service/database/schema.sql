-- Shipper Service Database Schema
-- Database: shipper_db

-- Drop tables if exist (for clean setup)
DROP TABLE IF EXISTS shipper_transactions;
DROP TABLE IF EXISTS shipper_balances;
DROP TABLE IF EXISTS shipper_locations;
DROP TABLE IF EXISTS shipper;

-- Create shipper table
CREATE TABLE shipper (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id BIGINT NOT NULL UNIQUE,
    vehicle_type VARCHAR(50),
    license_number VARCHAR(50) UNIQUE,
    id_card VARCHAR(20) UNIQUE,
    driver_image TEXT,
    is_online BOOLEAN DEFAULT FALSE,
    rating DECIMAL(2,1) DEFAULT 5.0,
    completed_deliveries INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create shipper_locations table
CREATE TABLE shipper_locations (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    shipper_id BIGINT NOT NULL UNIQUE,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shipper_id) REFERENCES shipper(id) ON DELETE CASCADE
);

-- Create shipper_balances table
CREATE TABLE shipper_balances (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    shipper_id BIGINT NOT NULL UNIQUE,
    balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    holding_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shipper_id) REFERENCES shipper(id) ON DELETE CASCADE
);

-- Create shipper_transactions table
CREATE TABLE shipper_transactions (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    shipper_id BIGINT NOT NULL,
    related_order_id BIGINT,
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAW', 'EARN', 'PENALTY', 'HOLD', 'RELEASE')),
    amount DECIMAL(12,2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shipper_id) REFERENCES shipper(id) ON DELETE CASCADE
);

-- Create indexes for better performance
CREATE INDEX idx_shipper_user_id ON shipper(user_id);
CREATE INDEX idx_shipper_is_online ON shipper(is_online);
CREATE INDEX idx_shipper_locations_shipper_id ON shipper_locations(shipper_id);
CREATE INDEX idx_shipper_locations_lat_lng ON shipper_locations(lat, lng);
CREATE INDEX idx_shipper_balances_shipper_id ON shipper_balances(shipper_id);
CREATE INDEX idx_shipper_transactions_shipper_id ON shipper_transactions(shipper_id);
CREATE INDEX idx_shipper_transactions_type ON shipper_transactions(transaction_type);
CREATE INDEX idx_shipper_transactions_order_id ON shipper_transactions(related_order_id);
CREATE INDEX idx_shipper_transactions_created_at ON shipper_transactions(created_at);

-- Sample data for testing
INSERT INTO shipper (user_id, vehicle_type, license_number, id_card, driver_image, is_online, rating, completed_deliveries)
VALUES 
    (1, 'MOTORBIKE', 'B1-123456', '123456789012', 'https://example.com/driver1.jpg', true, 4.8, 150),
    (2, 'CAR', 'C-654321', '987654321098', 'https://example.com/driver2.jpg', false, 4.5, 80),
    (3, 'BICYCLE', 'B2-789012', '456789012345', 'https://example.com/driver3.jpg', true, 4.9, 200);

INSERT INTO shipper_locations (shipper_id, lat, lng)
VALUES 
    (1, 10.762622, 106.660172), -- Ho Chi Minh City center
    (2, 10.754635, 106.663597), -- Near Ben Thanh Market
    (3, 10.779843, 106.695552); -- District 1

INSERT INTO shipper_balances (shipper_id, balance, holding_balance)
VALUES 
    (1, 1500000.00, 250000.00),
    (2, 800000.00, 100000.00),
    (3, 2200000.00, 0.00);

INSERT INTO shipper_transactions (shipper_id, related_order_id, transaction_type, amount, description)
VALUES 
    (1, NULL, 'DEPOSIT', 1000000.00, 'Nạp tiền đầu kỳ'),
    (1, 1001, 'EARN', 50000.00, 'Tiền thưởng từ đơn hàng #1001'),
    (1, 1002, 'HOLD', 250000.00, 'Giữ tạm tiền cho đơn hàng #1002'),
    (2, NULL, 'DEPOSIT', 500000.00, 'Nạp tiền lần đầu'),
    (2, 1003, 'EARN', 75000.00, 'Tiền thưởng từ đơn hàng #1003'),
    (3, NULL, 'DEPOSIT', 2000000.00, 'Nạp tiền tháng 1'),
    (3, 1004, 'EARN', 120000.00, 'Tiền thưởng từ đơn hàng #1004'),
    (3, 1005, 'EARN', 80000.00, 'Tiền thưởng từ đơn hàng #1005');
