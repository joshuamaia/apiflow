CREATE TABLE products
(
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(120)   NOT NULL,
    price NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    stock INTEGER        NOT NULL CHECK (stock >= 0)
);

CREATE TABLE orders
(
    id           BIGSERIAL PRIMARY KEY,
    created_at   TIMESTAMPTZ    NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    total_amount NUMERIC(14, 2) NOT NULL
);

CREATE TABLE order_items
(
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id BIGINT         NOT NULL REFERENCES products (id),
    quantity   INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
CREATE INDEX idx_orders_created_at ON orders (created_at DESC);
