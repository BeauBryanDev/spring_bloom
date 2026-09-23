-- Reclamos de clientes y pagos. Ambos cuelgan de flower_order: un reclamo y un
-- pago solo tienen sentido sobre una compra real.

CREATE TYPE complaint_type AS ENUM (
    'DAMAGED_FLOWERS',
    'LATE_DELIVERY',
    'NOT_DELIVERED',
    'WRONG_ITEM',
    'OTHER'
);

CREATE TYPE complaint_status AS ENUM (
    'OPEN',
    'IN_REVIEW',
    'RESOLVED',
    'REJECTED'
);

CREATE TYPE payment_status AS ENUM (
    'PENDING',
    'PAID',
    'FAILED',
    'CANCELLED',
    'REFUNDED',
    'EXPIRED'
);

-- Un reclamo: flores dañadas, entrega tarde, pedido equivocado.
-- order_id es NULL mientras no sepamos de que pedido habla el cliente: el chat
-- es anonimo y el reclamo se registra aunque el cliente no recuerde su numero.
CREATE TABLE complaint (
    id BIGSERIAL PRIMARY KEY,
    complaint_number VARCHAR(40) NOT NULL UNIQUE,
    order_id BIGINT REFERENCES flower_order(id) ON DELETE SET NULL,
    customer_id BIGINT REFERENCES customer(id) ON DELETE SET NULL,
    conversation_id UUID REFERENCES conversation(id) ON DELETE SET NULL,
    type complaint_type NOT NULL,
    status complaint_status NOT NULL DEFAULT 'OPEN',
    description TEXT NOT NULL,
    resolution TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,

    CONSTRAINT ck_complaint_description_not_blank
        CHECK (length(btrim(description)) > 0),

    -- Un reclamo esta cerrado exactamente cuando tiene fecha de cierre.
    CONSTRAINT ck_complaint_closed_has_date
        CHECK ((status IN ('RESOLVED', 'REJECTED')) = (resolved_at IS NOT NULL)),

    -- Cerrar un reclamo exige decir que se hizo.
    CONSTRAINT ck_complaint_closed_has_resolution
        CHECK (status NOT IN ('RESOLVED', 'REJECTED')
               OR length(btrim(coalesce(resolution, ''))) > 0)
);

CREATE INDEX ix_complaint_order ON complaint(order_id);
CREATE INDEX ix_complaint_status ON complaint(status);

-- Un intento de pago. Hay una fila por intento, no por pedido: un cobro puede
-- fallar y reintentarse. Lo que no puede repetirse es el exito, y de eso se
-- encarga el indice unico parcial de mas abajo.
--
-- Aqui NUNCA se guardan datos de tarjeta. Solo referencias del proveedor
-- (Stripe Checkout), para que ningun numero de tarjeta toque esta base.
CREATE TABLE payment (
    id BIGSERIAL PRIMARY KEY,
    payment_reference VARCHAR(40) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL REFERENCES flower_order(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL DEFAULT 'STRIPE',
    provider_session_id VARCHAR(255),
    provider_payment_intent VARCHAR(255),
    status payment_status NOT NULL DEFAULT 'PENDING',
    amount NUMERIC(10,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'COP',
    failure_reason VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    paid_at TIMESTAMPTZ,

    CONSTRAINT ck_payment_amount_positive
        CHECK (amount > 0),

    -- Un pago esta pagado exactamente cuando tiene fecha de pago. paid_at lo
    -- escribe el webhook firmado de Stripe, nunca el redirect del navegador.
    CONSTRAINT ck_payment_paid_has_date
        CHECK ((status = 'PAID') = (paid_at IS NOT NULL))
);

-- Un pedido se paga a lo sumo una vez. Los intentos fallidos pueden repetirse.
CREATE UNIQUE INDEX ux_payment_one_paid_per_order
    ON payment(order_id) WHERE status = 'PAID';

CREATE INDEX ix_payment_order ON payment(order_id);
CREATE INDEX ix_payment_session ON payment(provider_session_id);
