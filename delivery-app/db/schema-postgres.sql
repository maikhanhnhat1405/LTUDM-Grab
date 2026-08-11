-- ============================================================
-- Delivery App - Level 1 schema (PostgreSQL)
-- Chay:
--   createdb delivery_app        (hoac: psql -U postgres -c "CREATE DATABASE delivery_app;")
--   psql -U postgres -d delivery_app -f db/schema-postgres.sql
-- ============================================================

DROP TABLE IF EXISTS messages, orders, drivers, users CASCADE;

CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(200) NOT NULL,          -- format: salt_hex:sha256_hex
  full_name     VARCHAR(100) NOT NULL,
  phone         VARCHAR(20),
  role          VARCHAR(10)  NOT NULL CHECK (role IN ('CUSTOMER','DRIVER','ADMIN')),
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE drivers (
  user_id       BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  vehicle_type  VARCHAR(30)  DEFAULT 'BIKE',
  plate_number  VARCHAR(20),
  rating_avg    NUMERIC(3,2) DEFAULT 5.00
);

CREATE TABLE orders (
  id            BIGSERIAL PRIMARY KEY,
  customer_id   BIGINT NOT NULL REFERENCES users(id),
  driver_id     BIGINT NULL     REFERENCES users(id),
  pickup_addr   VARCHAR(255) NOT NULL,
  pickup_lat    DOUBLE PRECISION DEFAULT 0,
  pickup_lng    DOUBLE PRECISION DEFAULT 0,
  dropoff_addr  VARCHAR(255) NOT NULL,
  dropoff_lat   DOUBLE PRECISION DEFAULT 0,
  dropoff_lng   DOUBLE PRECISION DEFAULT 0,
  note          VARCHAR(255),
  price         NUMERIC(12,2) NOT NULL DEFAULT 0,
  status        VARCHAR(15) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','ACCEPTED','PICKED_UP','DELIVERING','COMPLETED','CANCELLED')),
  version       INT NOT NULL DEFAULT 0,          -- optimistic locking (dung o Level 3)
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_status   ON orders(status);
CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE INDEX idx_orders_driver   ON orders(driver_id);

CREATE TABLE messages (
  id         BIGSERIAL PRIMARY KEY,
  order_id   BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  sender_id  BIGINT NOT NULL REFERENCES users(id),
  content    TEXT NOT NULL,
  type       VARCHAR(20) NOT NULL DEFAULT 'TEXT',   -- TEXT | IMAGE (Level 2)
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_order ON messages(order_id, id);
