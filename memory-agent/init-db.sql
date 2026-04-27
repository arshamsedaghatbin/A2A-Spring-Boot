-- Run once against bankdb to set up the memory tables.
-- Requires pgvector extension: CREATE EXTENSION IF NOT EXISTS vector;

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS user_memory (
    user_id               VARCHAR(255) PRIMARY KEY,
    default_from_account  VARCHAR(255),
    last_transfer_to      VARCHAR(255),
    preferred_language    VARCHAR(10)  DEFAULT 'en',
    last_transaction_id   VARCHAR(255),
    last_transaction_date TIMESTAMP,
    total_transactions    INT          DEFAULT 0,
    created_at            TIMESTAMP    DEFAULT NOW(),
    updated_at            TIMESTAMP    DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS conversation_memory (
    id          BIGSERIAL PRIMARY KEY,
    user_id     VARCHAR(255) NOT NULL,
    content     TEXT         NOT NULL,
    embedding   vector(768),
    memory_type VARCHAR(50)  DEFAULT 'transfer',
    created_at  TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS conversation_memory_user_idx
    ON conversation_memory (user_id);

-- pgvector cosine distance index for fast similarity search
CREATE INDEX IF NOT EXISTS conversation_memory_embedding_idx
    ON conversation_memory USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE TABLE IF NOT EXISTS session_summary (
    id             BIGSERIAL PRIMARY KEY,
    session_id     VARCHAR(255),
    user_id        VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255),
    from_account   VARCHAR(255),
    to_account     VARCHAR(255),
    created_at     TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS session_summary_user_idx ON session_summary (user_id);
