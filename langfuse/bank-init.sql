-- Enable pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- User Memory: structured
CREATE TABLE IF NOT EXISTS user_memory (
    user_id VARCHAR(50) PRIMARY KEY,
    default_from_account VARCHAR(50),
    last_transfer_to VARCHAR(50),
    preferred_language VARCHAR(10) DEFAULT 'en',
    last_transaction_id VARCHAR(50),
    last_transaction_date TIMESTAMP,
    total_transactions INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Conversation Memory: vector
CREATE TABLE IF NOT EXISTS conversation_memory (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(50),
    content TEXT,
    embedding vector(768),
    memory_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Session Summary
CREATE TABLE IF NOT EXISTS session_summary (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(100),
    user_id VARCHAR(50),
    transaction_id VARCHAR(50),
    from_account VARCHAR(50),
    to_account VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_user_memory_user_id 
    ON user_memory(user_id);
CREATE INDEX IF NOT EXISTS idx_conversation_memory_user_id 
    ON conversation_memory(user_id);
CREATE INDEX IF NOT EXISTS idx_conversation_memory_embedding 
    ON conversation_memory USING ivfflat (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_session_summary_user_id 
    ON session_summary(user_id);
