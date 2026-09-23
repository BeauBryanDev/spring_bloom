-- Indices de consulta, disparadores de updated_at, la vista de disponibilidad y
-- la base de conocimiento vectorial que alimenta el RAG de Florabelle.


-- Busqueda rapida por precio.
CREATE INDEX idx_flower_stock_price ON flower_stock(base_price);

-- Consultas de stock bajo.
CREATE INDEX idx_flower_stock_quantity ON flower_stock(stock_quantity);

-- Cotizaciones por fecha.
CREATE INDEX idx_quotation_created ON quotation(created_at);

-- Pedidos por numero.
CREATE INDEX idx_flower_order_number ON flower_order(order_number);


-- updated_at lo escribe la base, no la aplicacion: un UPDATE que olvide la
-- columna deja la fila mintiendo sobre cuando cambio.
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_flower_stock_updated_at
    BEFORE UPDATE ON flower_stock
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_quotation_updated_at
    BEFORE UPDATE ON quotation
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_customer_updated_at
    BEFORE UPDATE ON customer
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_flower_order_updated_at
    BEFORE UPDATE ON flower_order
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();


-- Disponibilidad por especie, ya resuelta la union con stock.
CREATE VIEW flower_availability AS
SELECT
    fs.common_name,
    fs.species_key,
    fk.stock_quantity,
    fk.status,
    fk.base_price,
    CASE
        WHEN fk.stock_quantity > 0 THEN 'AVAILABLE'
        WHEN fk.eta_days IS NOT NULL THEN 'COMING_SOON'
        ELSE 'NOT_AVAILABLE'
    END AS availability
FROM flower_species fs
JOIN flower_stock fk ON fs.species_id = fk.species_id;


-- La extension vector viene en el binario de la imagen pgvector/pgvector:pg16.
-- hstore y uuid-ossp las exige PgVectorStore de Spring AI.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Esta tabla la crearia Spring AI sola, pero el esquema lo manda Flyway y
-- ddl-auto esta en validate: initialize-schema queda en false y la forma de
-- aqui debe coincidir exactamente con la que espera PgVectorStore.
-- 1536 dimensiones = text-embedding-3-small de OpenAI.
CREATE TABLE vector_store (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding VECTOR(1536)
);

-- HNSW y no IVFFlat: IVFFlat necesita filas para entrenarse y una migracion
-- corre sobre una tabla vacia. El nombre lo espera Spring AI.
CREATE INDEX spring_ai_vector_index ON vector_store
    USING hnsw (embedding vector_cosine_ops);

-- Spring AI filtra con metadata::jsonb @@ '...', asi que el indice va sobre la
-- expresion casteada; sobre la columna json cruda no lo usaria.
CREATE INDEX ix_vector_store_metadata ON vector_store
    USING gin ((metadata::jsonb));

-- Un renglon por documento de origen. El hash es lo que hace que reingerir sea
-- gratis: si el PDF no cambio, no se vuelve a embeber nada.
CREATE TABLE knowledge_document (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(300) NOT NULL,
    checksum CHAR(64) NOT NULL,
    chunk_count INTEGER NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_knowledge_document_chunks_positive
        CHECK (chunk_count > 0)
);
