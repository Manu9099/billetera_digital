CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS yape.dispute_timeline_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    dispute_id UUID NOT NULL,
    transaction_id UUID NOT NULL,

    actor_user_id UUID NULL,
    actor_role VARCHAR(30) NOT NULL,
    event_type VARCHAR(50) NOT NULL,

    title VARCHAR(160) NOT NULL,
    message TEXT NOT NULL,
    metadata_json TEXT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_dispute_timeline_dispute
        FOREIGN KEY (dispute_id)
        REFERENCES yape.disputes(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_dispute_timeline_transaction
        FOREIGN KEY (transaction_id)
        REFERENCES yape.transactions(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_dispute_timeline_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES yape.users(id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_dispute_timeline_dispute_created
    ON yape.dispute_timeline_events(dispute_id, created_at);

CREATE INDEX IF NOT EXISTS idx_dispute_timeline_transaction
    ON yape.dispute_timeline_events(transaction_id);

CREATE INDEX IF NOT EXISTS idx_dispute_timeline_actor
    ON yape.dispute_timeline_events(actor_user_id);