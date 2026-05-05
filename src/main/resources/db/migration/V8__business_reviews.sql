CREATE TABLE IF NOT EXISTS yape.business_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    business_profile_id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    customer_user_id UUID NOT NULL,

    rating INTEGER NOT NULL,
    review_comment TEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'VISIBLE',

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_business_reviews_business_profile
        FOREIGN KEY (business_profile_id)
        REFERENCES yape.business_profiles(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_business_reviews_transaction
        FOREIGN KEY (transaction_id)
        REFERENCES yape.transactions(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_business_reviews_customer
        FOREIGN KEY (customer_user_id)
        REFERENCES yape.users(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_business_reviews_rating
        CHECK (rating BETWEEN 1 AND 5),

    CONSTRAINT uq_business_reviews_transaction
        UNIQUE (transaction_id),

    CONSTRAINT uq_business_reviews_transaction_customer
        UNIQUE (transaction_id, customer_user_id)
);

CREATE INDEX IF NOT EXISTS idx_business_reviews_business_status_created
    ON yape.business_reviews(business_profile_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_business_reviews_customer
    ON yape.business_reviews(customer_user_id);

CREATE INDEX IF NOT EXISTS idx_business_reviews_transaction
    ON yape.business_reviews(transaction_id);