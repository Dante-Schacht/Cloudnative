CREATE SEQUENCE orders_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE orders (
    id            BIGINT                   NOT NULL PRIMARY KEY,
    customer_name VARCHAR(120)             NOT NULL,
    product       VARCHAR(120)             NOT NULL,
    quantity      INTEGER                  NOT NULL CHECK (quantity >= 1),
    notes         VARCHAR(500)             NOT NULL DEFAULT '',
    status        VARCHAR(20)              NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_orders_created_at ON orders (created_at);
