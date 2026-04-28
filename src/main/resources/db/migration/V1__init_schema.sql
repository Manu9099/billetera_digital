-- ================================================================
-- YapeSeguro DB Schema — V1
-- PostgreSQL 16 + Flyway 10
-- NOTA: Se usan VARCHAR con CHECK en lugar de CREATE TYPE ENUM
--       para compatibilidad directa con @Enumerated(EnumType.STRING)
--       de JPA/Hibernate sin necesidad de custom type converters.
-- ================================================================

CREATE SCHEMA IF NOT EXISTS yape;

-- ================================================================
-- 1. USERS
-- ================================================================
CREATE TABLE yape.users (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email            VARCHAR(255) NOT NULL,
    phone_number     VARCHAR(20)  NOT NULL,
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    google_id        VARCHAR(255),
    reniec_id        VARCHAR(8),

    -- KYC
    kyc_status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                        CHECK (kyc_status IN ('PENDING','VERIFIED','REJECTED')),
    kyc_document_url TEXT,
    kyc_verified_at  TIMESTAMPTZ,

    phone_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    biometric_enabled BOOLEAN     NOT NULL DEFAULT FALSE,

    -- Feature #12: Modo adulto mayor
    interface_mode   VARCHAR(20)  NOT NULL DEFAULT 'STANDARD'
                        CHECK (interface_mode IN ('STANDARD','SENIOR_MODE')),

    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email     UNIQUE (email),
    CONSTRAINT uq_users_phone     UNIQUE (phone_number),
    CONSTRAINT uq_users_google    UNIQUE (google_id),
    CONSTRAINT uq_users_reniec    UNIQUE (reniec_id),
    CONSTRAINT ck_reniec_digits   CHECK (reniec_id IS NULL OR reniec_id ~ '^\d{8}$')
);

CREATE INDEX idx_users_email  ON yape.users (email)        WHERE deleted = FALSE;
CREATE INDEX idx_users_phone  ON yape.users (phone_number) WHERE deleted = FALSE;
CREATE INDEX idx_users_kyc    ON yape.users (kyc_status);

-- ================================================================
-- 2. WALLETS  (Feature #4: bolsillos personal / negocio)
-- ================================================================
CREATE TABLE yape.wallets (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID          NOT NULL REFERENCES yape.users(id),
    wallet_type         VARCHAR(20)   NOT NULL
                            CHECK (wallet_type IN ('PERSONAL','BUSINESS')),

    -- NUMERIC para dinero, nunca FLOAT
    balance             NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    available_balance   NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    hold_amount         NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    currency            CHAR(3)       NOT NULL DEFAULT 'PEN',

    -- Analytics negocio (Feature #4 / #10)
    monthly_revenue     NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    monthly_expenses    NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    daily_tx_count      INTEGER       NOT NULL DEFAULT 0,
    monthly_reset_date  TIMESTAMPTZ,

    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    last_transaction_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_wallet_user_type      UNIQUE (user_id, wallet_type),
    CONSTRAINT ck_balance_positive      CHECK (balance           >= 0),
    CONSTRAINT ck_available_positive    CHECK (available_balance >= 0),
    CONSTRAINT ck_hold_positive         CHECK (hold_amount       >= 0),
    CONSTRAINT ck_available_le_balance  CHECK (available_balance <= balance)
);

CREATE INDEX idx_wallets_user   ON yape.wallets (user_id);
CREATE INDEX idx_wallets_active ON yape.wallets (active);

-- ================================================================
-- 3. QR CODES  (Feature #9: QR monto fijo)
-- ================================================================
CREATE TABLE yape.qr_codes (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_user_id   UUID          NOT NULL REFERENCES yape.users(id),
    creator_wallet_id UUID          NOT NULL REFERENCES yape.wallets(id),
    qr_type           VARCHAR(20)   NOT NULL DEFAULT 'PAYMENT'
                          CHECK (qr_type IN ('PAYMENT','FIXED_AMOUNT','INVENTORY')),
    qr_data           TEXT          NOT NULL,
    qr_image_url      TEXT,
    description       VARCHAR(255),
    fixed_amount      NUMERIC(14,2),
    currency          CHAR(3)       NOT NULL DEFAULT 'PEN',
    scans_count       INTEGER       NOT NULL DEFAULT 0,
    payments_count    INTEGER       NOT NULL DEFAULT 0,
    revenue           NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_fixed_amount_positive CHECK (fixed_amount IS NULL OR fixed_amount > 0)
);

CREATE INDEX idx_qr_creator ON yape.qr_codes (creator_user_id);
CREATE INDEX idx_qr_active  ON yape.qr_codes (active);

-- ================================================================
-- 4. TRANSACTIONS
-- ================================================================
CREATE TABLE yape.transactions (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_from_id        UUID          NOT NULL REFERENCES yape.wallets(id),
    wallet_to_id          UUID          NOT NULL REFERENCES yape.wallets(id),
    amount                NUMERIC(14,2) NOT NULL,
    currency              CHAR(3)       NOT NULL DEFAULT 'PEN',
    type                  VARCHAR(20)   NOT NULL
                              CHECK (type IN ('P2P','QR_PAYMENT','SCHEDULED','MARKETPLACE')),
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING','COMPLETED','FAILED','HELD','RELEASED','CANCELLED')),
    description           VARCHAR(255),
    concept               VARCHAR(100),
    reference             VARCHAR(60)   NOT NULL,

    -- Feature #1: Marketplace / Yape Seguro
    marketplace_dispute_id UUID,              -- FK circular; se agrega luego con ALTER TABLE
    marketplace_status     VARCHAR(20)  NOT NULL DEFAULT 'NORMAL'
                               CHECK (marketplace_status IN ('NORMAL','HELD','BUYER_CONFIRMED','DISPUTED')),
    hold_expires_at        TIMESTAMPTZ,

    -- Feature #9: QR con monto fijo
    qr_code_id             UUID REFERENCES yape.qr_codes(id),
    qr_description         VARCHAR(255),
    qr_fixed_amount        NUMERIC(14,2),

    -- Feature #7: Pago programado (trazabilidad)
    scheduled_payment_id   UUID,

    notes                  TEXT,
    completed_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_tx_reference      UNIQUE (reference),
    CONSTRAINT ck_tx_amount         CHECK (amount > 0),
    CONSTRAINT ck_tx_wallets_diff   CHECK (wallet_from_id <> wallet_to_id)
);

CREATE INDEX idx_tx_wallet_from ON yape.transactions (wallet_from_id);
CREATE INDEX idx_tx_wallet_to   ON yape.transactions (wallet_to_id);
CREATE INDEX idx_tx_status      ON yape.transactions (status);
CREATE INDEX idx_tx_type        ON yape.transactions (type);
CREATE INDEX idx_tx_created_at  ON yape.transactions (created_at DESC);
-- Índice parcial para analytics (Feature #10): solo tx completadas
CREATE INDEX idx_tx_completed   ON yape.transactions (wallet_from_id, created_at)
    WHERE status = 'COMPLETED';

-- ================================================================
-- 5. BUSINESS PROFILES  (Feature #3: Negocios verificados)
-- ================================================================
CREATE TABLE yape.business_profiles (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID          NOT NULL UNIQUE REFERENCES yape.users(id),
    business_wallet_id       UUID          NOT NULL REFERENCES yape.wallets(id),
    business_name            VARCHAR(255)  NOT NULL,
    ruc                      VARCHAR(11)   NOT NULL,
    business_category        VARCHAR(100),
    description              TEXT,
    address                  VARCHAR(255),
    latitude                 DOUBLE PRECISION,
    longitude                DOUBLE PRECISION,
    city                     VARCHAR(100),
    district                 VARCHAR(100),
    business_phone_number    VARCHAR(20),
    business_email           VARCHAR(255),
    website                  VARCHAR(255),
    verification_status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                                 CHECK (verification_status IN ('PENDING','VERIFIED','REJECTED','SUSPENDED')),
    verification_doc_url     TEXT,
    verification_date        TIMESTAMPTZ,
    average_rating           NUMERIC(3,2)  NOT NULL DEFAULT 0.00,
    total_reviews            INTEGER       NOT NULL DEFAULT 0,
    total_transactions       INTEGER       NOT NULL DEFAULT 0,
    total_revenue            NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    auto_confirm_receipts    BOOLEAN       NOT NULL DEFAULT FALSE,
    show_frequent_customers  BOOLEAN       NOT NULL DEFAULT FALSE,
    active                   BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_business_ruc      UNIQUE (ruc),
    CONSTRAINT ck_ruc_format        CHECK (ruc ~ '^\d{11}$'),
    CONSTRAINT ck_rating_range      CHECK (average_rating BETWEEN 0.00 AND 5.00)
);

CREATE INDEX idx_biz_ruc          ON yape.business_profiles (ruc);
CREATE INDEX idx_biz_verification ON yape.business_profiles (verification_status);
CREATE INDEX idx_biz_location     ON yape.business_profiles (latitude, longitude)
    WHERE verification_status = 'VERIFIED';

-- ================================================================
-- 6. INVENTORY  (Feature #5: Mini inventario)
-- ================================================================
CREATE TABLE yape.inventory_items (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    business_profile_id UUID          NOT NULL REFERENCES yape.business_profiles(id),
    qr_code_id          UUID          REFERENCES yape.qr_codes(id),
    product_name        VARCHAR(255)  NOT NULL,
    description         TEXT,
    product_category    VARCHAR(100),
    sku                 VARCHAR(50),
    image_url           TEXT,
    price               NUMERIC(14,2) NOT NULL,
    current_stock       INTEGER       NOT NULL DEFAULT 0,
    low_stock_threshold INTEGER       NOT NULL DEFAULT 5,
    total_units_sold    INTEGER       NOT NULL DEFAULT 0,
    qr_enabled          BOOLEAN       NOT NULL DEFAULT FALSE,
    sold_this_month     INTEGER       NOT NULL DEFAULT 0,
    sold_this_week      INTEGER       NOT NULL DEFAULT 0,
    revenue_this_month  NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    revenue_this_week   NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    last_sold_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_inventory_sku  UNIQUE (business_profile_id, sku),
    CONSTRAINT ck_price_positive CHECK (price > 0),
    CONSTRAINT ck_stock_positive CHECK (current_stock >= 0)
);

CREATE INDEX idx_inv_business   ON yape.inventory_items (business_profile_id);
CREATE INDEX idx_inv_qr         ON yape.inventory_items (qr_code_id) WHERE qr_code_id IS NOT NULL;
CREATE INDEX idx_inv_low_stock  ON yape.inventory_items (business_profile_id, current_stock)
    WHERE active = TRUE;

-- ================================================================
-- 7. DISPUTES  (Feature #2: Reclamo rápido)
-- ================================================================
CREATE TABLE yape.disputes (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id        UUID          NOT NULL REFERENCES yape.transactions(id),
    created_by_user_id    UUID          NOT NULL REFERENCES yape.users(id),
    respondent_user_id    UUID          NOT NULL REFERENCES yape.users(id),
    reason                VARCHAR(50)   NOT NULL
                              CHECK (reason IN (
                                  'UNAUTHORIZED_TRANSACTION','FRAUD','WRONG_AMOUNT',
                                  'PRODUCT_NOT_RECEIVED','PRODUCT_DEFECTIVE',
                                  'SERVICE_NOT_PROVIDED','DUPLICATE_CHARGE','OTHER'
                              )),
    description           TEXT          NOT NULL,
    disputed_amount       NUMERIC(14,2) NOT NULL,
    status                VARCHAR(30)   NOT NULL DEFAULT 'OPEN'
                              CHECK (status IN ('OPEN','EVIDENCE_REVIEW','IN_RESOLUTION','RESOLVED','CLOSED')),
    is_marketplace_dispute BOOLEAN      NOT NULL DEFAULT FALSE,
    recipient_phone       VARCHAR(20),
    qr_photo_url          TEXT,
    chat_transcript       TEXT,
    opened_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    evidence_submitted_at TIMESTAMPTZ,
    in_resolution_at      TIMESTAMPTZ,
    resolved_at           TIMESTAMPTZ,
    closed_at             TIMESTAMPTZ,
    expires_at            TIMESTAMPTZ   NOT NULL DEFAULT (NOW() + INTERVAL '30 days'),
    resolution            VARCHAR(20)   CHECK (resolution IN ('REFUND','PARTIAL_REFUND','DISMISSED')),
    refund_amount         NUMERIC(14,2),
    resolution_notes      TEXT,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_disputed_positive  CHECK (disputed_amount > 0),
    CONSTRAINT ck_refund_lte         CHECK (refund_amount IS NULL OR refund_amount <= disputed_amount),
    CONSTRAINT ck_users_different    CHECK (created_by_user_id <> respondent_user_id)
);

CREATE INDEX idx_disputes_tx         ON yape.disputes (transaction_id);
CREATE INDEX idx_disputes_reporter   ON yape.disputes (created_by_user_id);
CREATE INDEX idx_disputes_status     ON yape.disputes (status);
CREATE INDEX idx_disputes_expires    ON yape.disputes (expires_at) WHERE status = 'OPEN';

CREATE TABLE yape.dispute_evidence (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id  UUID        NOT NULL REFERENCES yape.disputes(id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL CHECK (type IN ('TEXT','IMAGE','AUDIO','VIDEO','DOCUMENT')),
    content_url TEXT        NOT NULL,
    description VARCHAR(255),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_evidence_dispute ON yape.dispute_evidence (dispute_id);

-- FK circular: transaction → dispute (para marketplace_dispute_id)
ALTER TABLE yape.transactions
    ADD CONSTRAINT fk_tx_marketplace_dispute
    FOREIGN KEY (marketplace_dispute_id) REFERENCES yape.disputes(id);

-- ================================================================
-- 8. GROUPS  (Feature #8: Cuentas grupales)
-- ================================================================
CREATE TABLE yape.groups (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_user_id   UUID          NOT NULL REFERENCES yape.users(id),
    group_name        VARCHAR(255)  NOT NULL,
    description       TEXT,
    group_type        VARCHAR(20)   NOT NULL DEFAULT 'OTHER'
                          CHECK (group_type IN ('TRIP','PARTY','WORK','OTHER')),
    total_amount      NUMERIC(14,2) NOT NULL,
    current_amount    NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    per_person_amount NUMERIC(14,2),
    currency          CHAR(3)       NOT NULL DEFAULT 'PEN',
    member_count      INTEGER       NOT NULL DEFAULT 0,
    status            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                          CHECK (status IN ('ACTIVE','COMPLETED','CANCELLED')),
    target_date       TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_group_total   CHECK (total_amount > 0),
    CONSTRAINT ck_group_current CHECK (current_amount >= 0 AND current_amount <= total_amount)
);

CREATE TABLE yape.group_members (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID          NOT NULL REFERENCES yape.groups(id) ON DELETE CASCADE,
    user_id         UUID          NOT NULL REFERENCES yape.users(id),
    user_name       VARCHAR(255),
    amount_to_pay   NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    amount_paid     NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','CONFIRMED','PAID')),
    paid_at         TIMESTAMPTZ,
    added_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_group_member   UNIQUE (group_id, user_id),
    CONSTRAINT ck_paid_lte       CHECK (amount_paid <= amount_to_pay)
);

CREATE INDEX idx_groups_creator   ON yape.groups (creator_user_id);
CREATE INDEX idx_groups_status    ON yape.groups (status);
CREATE INDEX idx_gm_group         ON yape.group_members (group_id);
CREATE INDEX idx_gm_user          ON yape.group_members (user_id);

-- ================================================================
-- 9. SCHEDULED PAYMENTS  (Feature #7: Pagos programados)
-- ================================================================
CREATE TABLE yape.scheduled_payments (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_from_id        UUID          NOT NULL REFERENCES yape.wallets(id),
    wallet_to_id          UUID          REFERENCES yape.wallets(id),
    recipient_user_id     UUID          REFERENCES yape.users(id),
    recipient_name        VARCHAR(255)  NOT NULL,
    recipient_phone       VARCHAR(20),
    amount                NUMERIC(14,2) NOT NULL,
    currency              CHAR(3)       NOT NULL DEFAULT 'PEN',
    concept               VARCHAR(100),
    description           TEXT,
    frequency             VARCHAR(20)   NOT NULL
                              CHECK (frequency IN ('DAILY','WEEKLY','BIWEEKLY','MONTHLY','CUSTOM')),
    day_of_month          SMALLINT      CHECK (day_of_month BETWEEN 1 AND 28),
    day_of_week           SMALLINT      CHECK (day_of_week  BETWEEN 1 AND 7),
    next_payment_date     TIMESTAMPTZ   NOT NULL,
    last_payment_date     TIMESTAMPTZ,
    start_date            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    end_date              TIMESTAMPTZ,
    auto_pay_enabled      BOOLEAN       NOT NULL DEFAULT FALSE,
    failure_retry_count   SMALLINT      NOT NULL DEFAULT 0,
    times_executed        INTEGER       NOT NULL DEFAULT 0,
    notify_days_in_advance SMALLINT     NOT NULL DEFAULT 1,
    status                VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                              CHECK (status IN ('ACTIVE','PAUSED','COMPLETED','CANCELLED')),
    paused_at             TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_scheduled_amount CHECK (amount > 0)
);

CREATE INDEX idx_sched_wallet    ON yape.scheduled_payments (wallet_from_id);
CREATE INDEX idx_sched_status    ON yape.scheduled_payments (status);
-- Job nocturno consulta solo los activos con vencimiento próximo
CREATE INDEX idx_sched_next_date ON yape.scheduled_payments (next_payment_date)
    WHERE status = 'ACTIVE';

-- ================================================================
-- 10. TRANSACTION RECEIPTS  (Feature #6: Comprobantes)
-- ================================================================
CREATE TABLE yape.transaction_receipts (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id   UUID          NOT NULL UNIQUE REFERENCES yape.transactions(id),
    receipt_number   VARCHAR(50)   NOT NULL,
    business_name    VARCHAR(255),
    business_ruc     VARCHAR(11),
    customer_name    VARCHAR(255),
    amount           NUMERIC(14,2) NOT NULL,
    currency         CHAR(3),
    concept          VARCHAR(255),
    description      TEXT,
    receipt_html     TEXT,
    receipt_pdf_url  TEXT,
    qr_code_url      TEXT,
    printed_count    INTEGER       NOT NULL DEFAULT 0,
    emailed_to       VARCHAR(255),
    sent_whatsapp    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_receipt_number UNIQUE (receipt_number)
);

CREATE INDEX idx_receipts_tx      ON yape.transaction_receipts (transaction_id);
CREATE INDEX idx_receipts_biz_ruc ON yape.transaction_receipts (business_ruc) WHERE business_ruc IS NOT NULL;

-- ================================================================
-- 11. EXPENSE ANALYTICS  (Feature #10: Ranking de gastos)
-- ================================================================
CREATE TABLE yape.expense_categories (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES yape.users(id),
    category_name VARCHAR(100) NOT NULL,
    icon_code     VARCHAR(50),
    color_hex     CHAR(7),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_expense_cat UNIQUE (user_id, category_name)
);

CREATE TABLE yape.expense_analytics (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id         UUID          NOT NULL REFERENCES yape.wallets(id),
    category_id       UUID          REFERENCES yape.expense_categories(id),
    year_month        CHAR(7)       NOT NULL,
    total_spent       NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    transaction_count INTEGER       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_analytics UNIQUE (wallet_id, category_id, year_month)
);

CREATE INDEX idx_analytics_wallet     ON yape.expense_analytics (wallet_id);
CREATE INDEX idx_analytics_year_month ON yape.expense_analytics (year_month);

-- ================================================================
-- 12. LOANS  (Feature #11: Préstamos transparentes)
-- ================================================================
CREATE TABLE yape.loans (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    borrower_user_id       UUID          NOT NULL REFERENCES yape.users(id),
    lender_user_id         UUID          NOT NULL REFERENCES yape.users(id),
    transaction_id         UUID          REFERENCES yape.transactions(id),
    original_amount        NUMERIC(14,2) NOT NULL,
    remaining_balance      NUMERIC(14,2) NOT NULL,
    interest_rate          NUMERIC(5,2)  NOT NULL DEFAULT 0.00,
    total_amount_to_return NUMERIC(14,2) NOT NULL,
    late_fee_per_day       NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    loan_status            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                               CHECK (loan_status IN ('ACTIVE','COMPLETED','DEFAULT','CANCELLED')),
    loan_date              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    due_date               TIMESTAMPTZ,
    completed_date         TIMESTAMPTZ,
    notes                  TEXT,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_loan_users_diff   CHECK (borrower_user_id <> lender_user_id),
    CONSTRAINT ck_loan_amount       CHECK (original_amount > 0),
    CONSTRAINT ck_interest_range    CHECK (interest_rate BETWEEN 0 AND 100),
    CONSTRAINT ck_total_gte_orig    CHECK (total_amount_to_return >= original_amount)
);

CREATE INDEX idx_loans_borrower ON yape.loans (borrower_user_id);
CREATE INDEX idx_loans_lender   ON yape.loans (lender_user_id);
CREATE INDEX idx_loans_status   ON yape.loans (loan_status);

-- ================================================================
-- 13. NOTIFICATIONS
-- ================================================================
CREATE TABLE yape.notifications (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES yape.users(id),
    title             VARCHAR(255),
    message           TEXT        NOT NULL,
    notification_type VARCHAR(30) NOT NULL
                          CHECK (notification_type IN
                              ('TRANSACTION','SCHEDULED_PAYMENT','DISPUTE','GROUP','ALERT','PROMO')),
    related_entity_id UUID,
    is_read           BOOLEAN     NOT NULL DEFAULT FALSE,
    read_at           TIMESTAMPTZ,
    sent_via          VARCHAR(20) CHECK (sent_via IN ('PUSH','SMS','EMAIL','IN_APP')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMPTZ
);

CREATE INDEX idx_notif_user     ON yape.notifications (user_id);
CREATE INDEX idx_notif_unread   ON yape.notifications (user_id, is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notif_created  ON yape.notifications (created_at DESC);

-- ================================================================
-- TRIGGER: updated_at automático
-- ================================================================
CREATE OR REPLACE FUNCTION yape.set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'users','wallets','qr_codes','transactions',
        'business_profiles','inventory_items','disputes',
        'groups','group_members','scheduled_payments',
        'transaction_receipts','expense_analytics','loans'
    ] LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_updated_at
             BEFORE UPDATE ON yape.%I
             FOR EACH ROW EXECUTE FUNCTION yape.set_updated_at();', t, t);
    END LOOP;
END;
$$;