-- ============================================================
-- Delivery App - Level 1 schema (MySQL 8)
-- Chay: mysql -u root -p < db/schema.sql
-- ============================================================
DROP DATABASE IF EXISTS delivery_app;
CREATE DATABASE delivery_app CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE delivery_app;

CREATE TABLE users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(200) NOT NULL,          -- format: salt_hex:sha256_hex
  full_name     VARCHAR(100) NOT NULL,
  phone         VARCHAR(20),
  role          ENUM('CUSTOMER','DRIVER','ADMIN') NOT NULL,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE drivers (
  user_id       BIGINT PRIMARY KEY,
  vehicle_type  VARCHAR(30)  DEFAULT 'BIKE',
  plate_number  VARCHAR(20),
  rating_avg    DECIMAL(3,2) DEFAULT 5.00,
  CONSTRAINT fk_driver_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE orders (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id   BIGINT NOT NULL,
  driver_id     BIGINT NULL,
  pickup_addr   VARCHAR(255) NOT NULL,
  pickup_lat    DOUBLE DEFAULT 0,
  pickup_lng    DOUBLE DEFAULT 0,
  dropoff_addr  VARCHAR(255) NOT NULL,
  dropoff_lat   DOUBLE DEFAULT 0,
  dropoff_lng   DOUBLE DEFAULT 0,
  note          VARCHAR(255),
  price         DECIMAL(12,2) NOT NULL DEFAULT 0,
  status        ENUM('PENDING','ACCEPTED','PICKED_UP','DELIVERING','COMPLETED','CANCELLED')
                NOT NULL DEFAULT 'PENDING',
  version       INT NOT NULL DEFAULT 0,          -- optimistic locking (dung o Level 3)
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES users(id),
  CONSTRAINT fk_order_driver   FOREIGN KEY (driver_id)   REFERENCES users(id),
  INDEX idx_status (status),
  INDEX idx_customer (customer_id),
  INDEX idx_driver (driver_id)
) ENGINE=InnoDB;

CREATE TABLE messages (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id   BIGINT NOT NULL,
  sender_id  BIGINT NOT NULL,
  content    TEXT NOT NULL,
  type       VARCHAR(20) NOT NULL DEFAULT 'TEXT',   -- TEXT | IMAGE (Level 2)
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_msg_order  FOREIGN KEY (order_id)  REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES users(id),
  INDEX idx_order (order_id, id)
) ENGINE=InnoDB;
