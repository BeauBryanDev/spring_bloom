
BEGIN;

-- ENUMS

CREATE TYPE flower_stock_status AS ENUM (
    'IN_STOCK',
    'INCOMING_RESTOCK',
    'IMPORT_ON_REQUEST',
    'NOT_FOR_SALE'
);

CREATE TYPE product_type AS ENUM (
    'INDIVIDUAL',
    'BOUQUET',
    'GARLAND'
);

CREATE TYPE quotation_status AS ENUM (
    'DRAFT',
    'SENT',
    'ACCEPTED',
    'EXPIRED',
    'REJECTED'
);

CREATE TYPE order_status AS ENUM (
    'PENDING',
    'CONFIRMED',
    'PREPARING',
    'READY_FOR_DISPATCH',
    'DISPATCHED',
    'DELIVERED',
    'CANCELLED'
);

CREATE TYPE shipment_status AS ENUM (
    'PENDING',
    'PREPARING',
    'DISPATCHED',
    'DELIVERED',
    'CANCELLED'
);

CREATE TYPE message_role AS ENUM (
    'USER',
    'ASSISTANT',
    'SYSTEM'
);

-- STORE OWNER / ADMIN

CREATE TABLE store_owner (
    owner_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ADMIN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_store_owner_role
        CHECK (role = 'ADMIN')
);

-- FLOWER DOMAIN

CREATE TABLE flower_species (
    species_id BIGSERIAL PRIMARY KEY,
    species_key VARCHAR(100) NOT NULL UNIQUE,
    common_name VARCHAR(150) NOT NULL,
    scientific_name VARCHAR(150),
    origin_country VARCHAR(100),
    description TEXT,
    symbolic_meaning VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Physical stock + commercial price belongs to a flower species, not to a bouquet/product.
CREATE TABLE flower_stock (
    flower_stock_id BIGSERIAL PRIMARY KEY,
    species_id BIGINT NOT NULL UNIQUE REFERENCES flower_species(species_id),
    status flower_stock_status NOT NULL DEFAULT 'IN_STOCK',
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    eta_days INTEGER,
    base_price NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    import_price_multiplier NUMERIC(6,3) NOT NULL DEFAULT 1.000,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_stock_stock_nonnegative
        CHECK (stock_quantity >= 0),
    CONSTRAINT ck_stock_eta_nonnegative
        CHECK (eta_days IS NULL OR eta_days >= 0),
    CONSTRAINT ck_stock_base_price_nonnegative
        CHECK (base_price >= 0),
    CONSTRAINT ck_stock_import_multiplier_positive
        CHECK (import_price_multiplier > 0)
);

-- CUSTOMERS

-- Customers do not need an account to use the public chat.
-- A customer record is only persisted when there is enough information
-- for a quotation/order or when the application decides to retain it.
CREATE TABLE customer (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150),
    phone VARCHAR(40),
    delivery_address VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- CONVERSATIONS / MESSAGES

-- Anonymous public-chat session. Authentication is not required.
CREATE TABLE conversation (
    id UUID PRIMARY KEY,
    customer_id BIGINT REFERENCES customer(id) ON DELETE SET NULL,
    session_key VARCHAR(150) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    role message_role NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- QUOTATIONS

-- A quotation is the commercial proposal Florabelle presents to the user.
CREATE TABLE quotation (
    id BIGSERIAL PRIMARY KEY,
    quotation_number VARCHAR(40) NOT NULL UNIQUE,
    customer_id BIGINT REFERENCES customer(id) ON DELETE SET NULL,
    conversation_id UUID REFERENCES conversation(id) ON DELETE SET NULL,
    status quotation_status NOT NULL DEFAULT 'DRAFT',
    subtotal NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    discount_amount NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_quotation_subtotal_nonnegative
        CHECK (subtotal >= 0),
    CONSTRAINT ck_quotation_discount_amount_nonnegative
        CHECK (discount_amount >= 0),
    CONSTRAINT ck_quotation_total_nonnegative
        CHECK (total_amount >= 0)
);

-- Cada linea de una cotizacion: una flor individual, o un bouquet/garland custom.
CREATE TABLE quotation_item (
    id BIGSERIAL PRIMARY KEY,
    quotation_id BIGINT NOT NULL REFERENCES quotation(id) ON DELETE CASCADE,
    product_type product_type NOT NULL,
    discount_percentage NUMERIC(5,2),
    subtotal NUMERIC(10,2) NOT NULL DEFAULT 0.00,

    CONSTRAINT ck_quotation_item_discount_range
        CHECK (discount_percentage IS NULL OR (discount_percentage >= 0 AND discount_percentage <= 100)),
    CONSTRAINT ck_quotation_item_discount_only_bundle
        CHECK (
            (product_type = 'INDIVIDUAL' AND discount_percentage IS NULL)
            OR (product_type IN ('BOUQUET', 'GARLAND') AND discount_percentage IS NOT NULL)
        ),
    CONSTRAINT ck_quotation_item_subtotal_nonnegative
        CHECK (subtotal >= 0)
);

-- Composicion real de esa linea: que especies y cuantas unidades de cada una.
CREATE TABLE quotation_item_species (
    id BIGSERIAL PRIMARY KEY,
    quotation_item_id BIGINT NOT NULL REFERENCES quotation_item(id) ON DELETE CASCADE,
    species_id BIGINT NOT NULL REFERENCES flower_species(species_id),
    common_name_snapshot VARCHAR(150) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price_snapshot NUMERIC(10,2) NOT NULL,
    line_total NUMERIC(10,2) NOT NULL,

    CONSTRAINT uq_quotation_item_species UNIQUE (quotation_item_id, species_id),
    CONSTRAINT ck_qis_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_qis_unit_price_nonnegative CHECK (unit_price_snapshot >= 0),
    CONSTRAINT ck_qis_line_total_nonnegative CHECK (line_total >= 0)
);

-- ORDERS
-- A quotation may become exactly one order.
CREATE TABLE flower_order (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(40) NOT NULL UNIQUE,
    quotation_id BIGINT NOT NULL UNIQUE REFERENCES quotation(id),
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    status order_status NOT NULL DEFAULT 'PENDING',
    subtotal NUMERIC(10,2) NOT NULL,
    discount_amount NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_order_subtotal_nonnegative
        CHECK (subtotal >= 0),
    CONSTRAINT ck_order_discount_nonnegative
        CHECK (discount_amount >= 0),
    CONSTRAINT ck_order_total_nonnegative
        CHECK (total_amount >= 0)
);

CREATE TABLE order_item (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES flower_order(id) ON DELETE CASCADE,
    product_type product_type NOT NULL,
    discount_percentage NUMERIC(5,2),
    subtotal NUMERIC(10,2) NOT NULL DEFAULT 0.00,

    CONSTRAINT ck_order_item_discount_range
        CHECK (discount_percentage IS NULL OR (discount_percentage >= 0 AND discount_percentage <= 100)),
    CONSTRAINT ck_order_item_subtotal_nonnegative
        CHECK (subtotal >= 0)
);

CREATE TABLE order_item_species (
    id BIGSERIAL PRIMARY KEY,
    order_item_id BIGINT NOT NULL REFERENCES order_item(id) ON DELETE CASCADE,
    species_id BIGINT NOT NULL REFERENCES flower_species(species_id),
    common_name_snapshot VARCHAR(150) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price_snapshot NUMERIC(10,2) NOT NULL,
    line_total NUMERIC(10,2) NOT NULL,

    CONSTRAINT uq_order_item_species UNIQUE (order_item_id, species_id),
    CONSTRAINT ck_ois_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_ois_unit_price_nonnegative CHECK (unit_price_snapshot >= 0),
    CONSTRAINT ck_ois_line_total_nonnegative CHECK (line_total >= 0)
);

-- SHIPMENTS

CREATE TABLE shipment (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES flower_order(id) ON DELETE CASCADE,
    status shipment_status NOT NULL DEFAULT 'PENDING',
    recipient_name VARCHAR(150) NOT NULL,
    delivery_address VARCHAR(300) NOT NULL,
    tracking_code VARCHAR(100),
    scheduled_date DATE,
    dispatched_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- INDEXES

CREATE INDEX idx_flower_species_common_name
    ON flower_species(common_name);

CREATE INDEX idx_message_conversation_created
    ON message(conversation_id, created_at);

CREATE INDEX idx_quotation_customer
    ON quotation(customer_id);

CREATE INDEX idx_quotation_conversation
    ON quotation(conversation_id);

CREATE INDEX idx_quotation_status
    ON quotation(status);

CREATE INDEX idx_quotation_item_quotation
    ON quotation_item(quotation_id);

CREATE INDEX idx_quotation_item_species_item
    ON quotation_item_species(quotation_item_id);

CREATE INDEX idx_quotation_item_species_species
    ON quotation_item_species(species_id);

CREATE INDEX idx_order_customer
    ON flower_order(customer_id);

CREATE INDEX idx_order_status
    ON flower_order(status);

CREATE INDEX idx_order_created_at
    ON flower_order(created_at);

CREATE INDEX idx_order_item_order
    ON order_item(order_id);

CREATE INDEX idx_order_item_species_item
    ON order_item_species(order_item_id);

CREATE INDEX idx_order_item_species_species
    ON order_item_species(species_id);

CREATE INDEX idx_shipment_status
    ON shipment(status);

COMMIT;
