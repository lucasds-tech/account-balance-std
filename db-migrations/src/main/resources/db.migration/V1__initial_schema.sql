-- ***********************--
-- * V1 - Schema inicial *--
-- ***********************--

CREATE TABLE IF NOT EXISTS accounts (
    id          UUID PRIMARY KEY,
    owner       UUID        NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS balances (
    account_id  UUID PRIMARY KEY REFERENCES accounts (id),
    amount      NUMERIC(18, 2) NOT NULL,
    currency    VARCHAR(3)     NOT NULL DEFAULT 'BRL',
    updated_at  TIMESTAMPTZ    NOT NULL
);

CREATE TABLE IF NOT EXISTS processed_transactions (
    transaction_id UUID PRIMARY KEY,
    processed_at   TIMESTAMPTZ NOT NULL
);