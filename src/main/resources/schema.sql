-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Raw investment data table (CSV import target)
CREATE TABLE IF NOT EXISTS raw_investment_data (
    id BIGSERIAL PRIMARY KEY,
    instruction TEXT,
    output TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Investment embeddings table (final processed data)
CREATE TABLE IF NOT EXISTS new_investment_embeddings (
    id BIGSERIAL PRIMARY KEY,
    chunk_text TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB,
    source_id BIGINT REFERENCES raw_investment_data(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for vector similarity search
CREATE INDEX IF NOT EXISTS idx_embedding_vector 
ON new_investment_embeddings USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);