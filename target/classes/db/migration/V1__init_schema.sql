-- ============================================================
-- YapeSeguro DB Migration V1
-- Flyway: V1__init_schema.sql
-- PostgreSQL 14+
-- Features: 12 módulos de la billetera digital
-- ============================================================

CREATE SCHEMA IF NOT EXISTS yape;

-- ============================================================
-- ENUMS — tipados en PostgreSQL para integridad referencial
-- ============================================================
CREATE TYPE yape.kyc_status      AS ENUM ('PENDING', 'VERIFIED', 'REJECTED');
CREATE TYPE yape.interface_mode  AS ENUM ('STANDARD', 'SENIOR_MODE');
CREATE TYPE yape.wallet_type     AS ENUM ('PERSONAL', 'BUSINESS');
CREATE TYPE yape.tx_type         AS ENUM ('P2P', 'QR_PAYMENT', 'SCHEDULED', 'MARKETPLACE');
CREATE TYPE yape.tx_status       AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'HELD', 'RELEASED', 'CANCELLED');
CREATE TYPE yape.mp_status       AS ENUM ('NORMAL', 'HELD', 'BUYER_CONFIRMED', 'DISPUTED');
CREATE TYPE yape.dispute_reason  AS ENUM ('UNAUTHORIZED_TRANSACTION','FRAUD','WRONG_AMOUNT',
                                           'PRODUCT_NOT_RECEIVED','PRODUCT_DEFECTIVE',
                                           'SERVICE_NOT_PROVIDED','DUPLICATE_CHARGE','OTHER');
CREATE TYPE yape.dispute_status  AS ENUM ('OPEN','EVIDENCE_REVIEW','IN_RESOLUTION','RESOLVED','CLOSED');
CREATE TYPE yape.dispute_resolution AS ENUM ('REFUND','PARTIAL_REFUND','DISMISSED');
CREATE TYPE yape.biz_verification AS ENUM ('PENDING','VERIFIED','REJECTED','SUSPENDED');
CREATE TYPE yape.group_type      AS ENUM ('TRIP','PARTY','WORK','OTHER');
CREATE TYPE yape.group_status    AS ENUM ('ACTIVE','COMPLETED','CANCELLED');
CREATE TYPE yape.member_status   AS ENUM ('PENDING','CONFIRMED','PAID');
CREATE TYPE yape.pay_frequency   AS ENUM ('DAILY','WEEKLY','BIWEEKLY','MONTHLY','CUSTOM');
CREATE TYPE yape.scheduled_status AS ENUM ('ACTIVE','PAUSED','COMPLETED','CANCELLED');
CREATE TYPE yape.loan_status     AS ENUM ('ACTIVE','COMPLETED','DEFAULT','CANCELLED');
CREATE TYPE yape.notif_type      AS ENUM ('TRANSACTION','SCHEDULED_PAYMENT','DISPUTE','GROUP','ALERT','PROMO');
CREATE TYPE yape.qr_type         AS ENUM ('PAYMENT','FIXED_AMOUNT','INVENTORY');
CREATE TYPE yape.evidence_type   AS ENUM ('TEXT','IMAGE','AUDIO','VIDEO','DOCUMENT');

-- ============================================================
-- 1. USERS
-- ============================================================
CREATE TABLE yape.users (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email            VARCHAR(255) NOT NULL,
    phone_number     VARCHAR(20)  NOT NULL,
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    google_id        VARCHAR(255),
    reniec_id        VARCHAR(8),                   -- DNI peruano (8 dígitos)

    -- KYC
    kyc_status       yape.kyc_status    NOT NULL DEFAULT 'PENDING',
    kyc_document_url TEXT,
    kyc_verified_at  TIMESTAMPTZ,

    -- Verificaciones básicas
    phone_verified   BOOLEAN     NOT NULL DEFAULT FALSE,

    -- Seguridad
    biometric_enabled BOOLEAN    NOT NULL DEFAULT FALSE,

    -- Feature #12: Modo adulto mayor
    interface_mode   yape.interface_mode NOT NULL DEFAULT 'STANDARD',

    -- Soft delete
    deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMPTZ,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email        UNIQUE (email),
    CONSTRAINT uq_users_phone        UNIQUE (phone_number),
    CONSTRAINT uq_users_google_id    UNIQUE (google_id),
    CONSTRAINT uq_users_reniec       UNIQUE (reniec_id),
    CONSTRAINT ck_reniec_format      CHECK (reniec_id ~ '^\d{8}$')
);

CREATE INDEX idx_users_email        ON yape.users (email)       WHERE deleted = FALSE;
CREATE INDEX idx_users_phone        ON yape.users (phone_number) WHERE deleted = FALSE;
CREATE INDEX idx_users_kyc_status   ON yape.users (kyc_status);

-- ============================================================
-- 2. WALLETS — Feature #4: Bolsillos personal y negocio
-- ============================================================
CREATE TABLE yape.wallets (
    id                     UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID       NOT NULL REFERENCES yape.users(id),
    wallet_type            yape.wallet_type NOT NULL,

    -- Saldos (NUMERIC para operaciones financieras exactas, nunca FLOAT)
    balance                NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    available_balance      NUMERIC(14,2) NOT NULL DEFAULT 0.00,  -- balance - hold_amount
    hold_amount            NUMERIC(14,2) NOT NULL DEFAULT 0.00,  -- retenido en marketplace/disputas
    currency               CHAR(3)    NOT NULL DEFAULT 'PEN',

    -- Analytics para billetera negocio (Feature #4 y #10)
    monthly_revenue        NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    monthly_expenses       NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    daily_tx_count         INTEGER    NOT NULL DEFAULT 0,
    monthly_reset_date     TIMESTAMPTZ,

    active                 BOOLEAN    NOT NULL DEFAULT TRUE,
    last_transaction_at    TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Un usuario sólo puede tener UN wallet de cada tipo
    CONSTRAINT uq_wallet_user_type UNIQUE (user_id, wallet_type),

    -- Invariantes financieros — nunca saldos negativos
    CONSTRAINT ck_balance_positive          CHECK (balance           >= 0.00),
    CONSTRAINT ck_available_balance_ok      CHECK (available_balance >= 0.00),
    CONSTRAINT ck_hold_amount_positive      CHECK (hold_amount       >= 0.00),
    CONSTRAINT ck_available_le_balance      CHECK (available_balance <= balance)
);

CREATE INDEX idx_wallets_user_id ON yape.wallets (user_id);
CREATE INDEX idx_wallets_active  ON yape.wallets (active);

-- ============================================================
-- 3. QR CODES — Feature #9: QR con monto fijo y descripción
-- ============================================================
CREATE TABLE yape.qr_codes (
    id                  UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_user_id     UUID      NOT NULL REFERENCES yape.users(id),
    creator_wallet_id   UUID      NOT NULL REFERENCES yape.wallets(id),
    qr_type             yape.qr_type NOT NULL DEFAULT 'PAYMENT',
    qr_data             TEXT      NOT NULL,   -- JSON codificado en el QR
    qr_image_url        TEXT,

    -- Para QR de monto fijo
    description         VARCHAR(255),
    fixed_amount        NUMERIC(14,2),
    currency            CHAR(3)   NOT NULL DEFAULT 'PEN',

    -- Analytics
    scans_count         INTEGER   NOT NULL DEFAULT 0,
    payments_count      INTEGER   NOT NULL DEFAULT 0,
    revenue             NUMERIC(14,2) NOT NULL DEFAULT 0.00,

    active              BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_fixed_amount_positive CHECK (fixed_amount IS NULL OR fixed_amount > 0.00)
);

CREATE INDEX idx_qr_creator_user ON yape.qr_codes (creator_user_id);
CREATE INDEX idx_qr_active       ON yape.qr_codes (active);

-- ============================================================
-- 4. TRANSACTIONS — P2P, QR, Scheduled, Marketplace
-- ============================================================
CREATE TABLE yape.transactions (
    id                  UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_from_id      UUID      NOT NULL REFERENCES yape.wallets(id),
    wallet_to_id        UUID      NOT NULL REFERENCES yape.wallets(id),
    amount              NUMERIC(14,2) NOT NULL,
    currency            CHAR(3)   NOT NULL DEFAULT 'PEN',

    type                yape.tx_type   NOT NULL,
    status              yape.tx_status NOT NULL DEFAULT 'PENDING',
    description         VARCHAR(255),
    concept             VARCHAR(100),
    reference           VARCHAR(60)   NOT NULL,   -- referencia única visible al usuario

    -- Feature #1: Marketplace Protection
    marketplace_dispute_id  UUID,                 -- FK circular, se agrega luego con ALTER TABLE
    marketplace_status      yape.mp_status NOT NULL DEFAULT 'NORMAL',
    hold_expires_at     TIMESTAMPTZ,

    -- Feature #9: QR con monto fijo
    qr_code_id          UUID REFERENCES yape.qr_codes(id),
    qr_description      VARCHAR(255),
    qr_fixed_amount     NUMERIC(14,2),

    -- Feature #7: Pagos programados (referencia al trigger)
    scheduled_payment_id UUID,

    notes               TEXT,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_transaction_reference UNIQUE (reference),
    CONSTRAINT ck_amount_positive       CHECK (amount > 0.00),
    CONSTRAINT ck_wallet_different      CHECK (wallet_from_id <> wallet_to_id)
);

CREATE INDEX idx_tx_wallet_from  ON yape.transactions (wallet_from_id);
CREATE INDEX idx_tx_wallet_to    ON yape.transactions (wallet_to_id);
CREATE INDEX idx_tx_status       ON yape.transactions (status);
CREATE INDEX idx_tx_type         ON yape.transactions (type);
CREATE INDEX idx_tx_created_at   ON yape.transactions (created_at DESC);
CREATE INDEX idx_tx_reference    ON yape.transactions (reference);
-- Para analytics de gastos mensuales (Feature #10)
CREATE INDEX idx_tx_from_month   ON yape.transactions (wallet_from_id, created_at)
    WHERE status = 'COMPLETED';

-- ============================================================
-- 5. BUSINESS PROFILES — Feature #3: Negocios verificados
-- ============================================================
CREATE TABLE yape.business_profiles (
    id                     UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID      NOT NULL UNIQUE REFERENCES yape.users(id),
    business_wallet_id     UUID      NOT NULL REFERENCES yape.wallets(id),

    -- Datos del negocio
    business_name          VARCHAR(255) NOT NULL,
    ruc                    VARCHAR(11)  NOT NULL,
    business_category      VARCHAR(100),
    description            TEXT,

    -- Ubicación
    address                VARCHAR(255),
    latitude               DOUBLE PRECISION,
    longitude              DOUBLE PRECISION,
    city                   VARCHAR(100),
    district               VARCHAR(100),

    -- Contacto
    business_phone_number  VARCHAR(20),
    business_email         VARCHAR(255),
    website                VARCHAR(255),

    -- Verificación
    verification_status    yape.biz_verification NOT NULL DEFAULT 'PENDING',
    verification_doc_url   TEXT,
    verification_date      TIMESTAMPTZ,

    -- Reputación
    average_rating         NUMERIC(3,2) NOT NULL DEFAULT 0.00,
    total_reviews          INTEGER      NOT NULL DEFAULT 0,
    total_transactions     INTEGER      NOT NULL DEFAULT 0,
    total_revenue          NUMERIC(14,2) NOT NULL DEFAULT 0.00,

    -- Preferencias
    auto_confirm_receipts      BOOLEAN NOT NULL DEFAULT FALSE,
    show_frequent_customers    BOOLEAN NOT NULL DEFAULT FALSE,

    active                 BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_business_ruc            UNIQUE (ruc),
    CONSTRAINT ck_ruc_format              CHECK (ruc ~ '^\d{11}$'),
    CONSTRAINT ck_rating_range            CHECK (average_rating BETWEEN 0.00 AND 5.00)
);

CREATE INDEX idx_biz_ruc            ON yape.business_profiles (ruc);
CREATE INDEX idx_biz_verification   ON yape.business_profiles (verification_status);
-- Búsqueda por coordenadas (para mostrar negocios cercanos)
CREATE INDEX idx_biz_location       ON yape.business_profiles (latitude, longitude)
    WHERE verification_status = 'VERIFIED';

-- ============================================================
-- 6. INVENTORY — Feature #5: Mini inventario para bodegas
-- ============================================================
CREATE TABLE yape.inventory_items (
    id                  UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    business_profile_id UUID      NOT NULL REFERENCES yape.business_profiles(id),
    qr_code_id          UUID      REFERENCES yape.qr_codes(id),

    product_name        VARCHAR(255)  NOT NULL,
    description         TEXT,
    product_category    VARCHAR(100),
    sku                 VARCHAR(50),
    image_url           TEXT,

    -- Precio y stock
    price               NUMERIC(14,2) NOT NULL,
    current_stock       INTEGER       NOT NULL DEFAULT 0,
    low_stock_threshold INTEGER       NOT NULL DEFAULT 5,
    total_units_sold    INTEGER       NOT NULL DEFAULT 0,

    -- QR habilitado para cobro directo
    qr_enabled          BOOLEAN       NOT NULL DEFAULT FALSE,

    -- Analytics rápidos
    sold_this_month     INTEGER       NOT NULL DEFAULT 0,
    sold_this_week      INTEGER       NOT NULL DEFAULT 0,
    revenue_this_month  NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    revenue_this_week   NUMERIC(14,2) NOT NULL DEFAULT 0.00,

    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    last_sold_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_inventory_sku UNIQUE (business_profile_id, sku),
    CONSTRAINT ck_price_positive CHECK (price > 0.00),
    CONSTRAINT ck_stock_positive CHECK (current_stock >= 0)
);

CREATE INDEX idx_inventory_business  ON yape.inventory_items (business_profile_id);
CREATE INDEX idx_inventory_qr        ON yape.inventory_items (qr_code_id) WHERE qr_code_id IS NOT NULL;
CREATE INDEX idx_inventory_low_stock ON yape.inventory_items (business_profile_id, current_stock)
    WHERE active = TRUE;

-- ============================================================
-- 7. DISPUTES — Feature #2: Reclamo rápido por estafa
-- ============================================================
CREATE TABLE yape.disputes (
    id                       UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id           UUID      NOT NULL REFERENCES yape.transactions(id),
    created_by_user_id       UUID      NOT NULL REFERENCES yape.users(id),
    respondent_user_id       UUID      NOT NULL REFERENCES yape.users(id),

    reason                   yape.dispute_reason  NOT NULL,
    description              TEXT      NOT NULL,
    disputed_amount          NUMERIC(14,2) NOT NULL,
    status                   yape.dispute_status  NOT NULL DEFAULT 'OPEN',

    -- Feature #1: Marketplace
    is_marketplace_dispute   BOOLEAN   NOT NULL DEFAULT FALSE,

    -- Evidencia (Feature #2)
    recipient_phone          VARCHAR(20),
    qr_photo_url             TEXT,
    chat_transcript          TEXT,

    -- Timeline
    opened_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    evidence_submitted_at    TIMESTAMPTZ,
    in_resolution_at         TIMESTAMPTZ,
    resolved_at              TIMESTAMPTZ,
    closed_at                TIMESTAMPTZ,
    expires_at               TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '30 days'),

    -- Resolución
    resolution               yape.dispute_resolution,
    refund_amount            NUMERIC(14,2),
    resolution_notes         TEXT,

    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_disputed_amount_positive  CHECK (disputed_amount > 0.00),
    CONSTRAINT ck_refund_lte_disputed       CHECK (refund_amount IS NULL OR refund_amount <= disputed_amount),
    CONSTRAINT ck_different_users           CHECK (created_by_user_id <> respondent_user_id)
);

CREATE INDEX idx_disputes_transaction    ON yape.disputes (transaction_id);
CREATE INDEX idx_disputes_created_by     ON yape.disputes (created_by_user_id);
CREATE INDEX idx_disputes_respondent     ON yape.disputes (respondent_user_id);
CREATE INDEX idx_disputes_status         ON yape.disputes (status);
CREATE INDEX idx_disputes_expires_at     ON yape.disputes (expires_at) WHERE status = 'OPEN';

-- Evidencias adjuntas al reclamo
CREATE TABLE yape.dispute_evidence (
    id          UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id  UUID     NOT NULL REFERENCES yape.disputes(id) ON DELETE CASCADE,
    type        yape.evidence_type NOT NULL,
    content_url TEXT     NOT NULL,
    description VARCHAR(255),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_evidence_dispute ON yape.dispute_evidence (dispute_id);

-- FK circular: transaction → dispute (marketplace)
ALTER TABLE yape.transactions
    ADD CONSTRAINT fk_tx_marketplace_dispute
    FOREIGN KEY (marketplace_dispute_id) REFERENCES yape.disputes(id);

-- ============================================================
-- 8. GROUPS — Feature #8: Cuentas grupales (polladas, viajes)
-- ============================================================
CREATE TABLE yape.groups (
    id                  UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_user_id     UUID      NOT NULL REFERENCES yape.users(id),

    group_name          VARCHAR(255) NOT NULL,
    description         TEXT,
    group_type          yape.group_type   NOT NULL DEFAULT 'OTHER',

    total_amount        NUMERIC(14,2) NOT NULL,
    current_amount      NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    per_person_amount   NUMERIC(14,2),
    currency            CHAR(3)   NOT NULL DEFAULT 'PEN',

    member_count        INTEGER   NOT NULL DEFAULT 0,
    status              yape.group_status NOT NULL DEFAULT 'ACTIVE',
    target_date         TIMESTAMPTZ,

    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_group_total_positive    CHECK (total_amount > 0.00),
    CONSTRAINT ck_group_current_ok        CHECK (current_amount >= 0.00 AND current_amount <= total_amount)
);

CREATE INDEX idx_groups_creator ON yape.groups (creator_user_id);
CREATE INDEX idx_groups_status  ON yape.groups (status);

-- Miembros del grupo
CREATE TABLE yape.group_members (
    id              UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID     NOT NULL REFERENCES yape.groups(id) ON DELETE CASCADE,
    user_id         UUID     NOT NULL REFERENCES yape.users(id),
    user_name       VARCHAR(255),

    amount_to_pay   NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    amount_paid     NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    status          yape.member_status NOT NULL DEFAULT 'PENDING',

    paid_at         TIMESTAMPTZ,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_group_member        UNIQUE (group_id, user_id),
    CONSTRAINT ck_amount_to_pay_ok    CHECK (amount_to_pay >= 0.00),
    CONSTRAINT ck_amount_paid_ok      CHECK (amount_paid   >= 0.00),
    CONSTRAINT ck_paid_lte_to_pay     CHECK (amount_paid   <= amount_to_pay)
);

CREATE INDEX idx_group_members_group ON yape.group_members (group_id);
CREATE INDEX idx_group_members_user  ON yape.group_members (user_id);

-- ============================================================
-- 9. SCHEDULED PAYMENTS — Feature #7: Pagos programados
-- ============================================================
CREATE TABLE yape.scheduled_payments (
    id                      UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_from_id          UUID     NOT NULL REFERENCES yape.wallets(id),
    wallet_to_id            UUID     REFERENCES yape.wallets(id),   -- null si destinatario es externo
    recipient_user_id       UUID     REFERENCES yape.users(id),

    recipient_name          VARCHAR(255) NOT NULL,
    recipient_phone         VARCHAR(20),
    amount                  NUMERIC(14,2) NOT NULL,
    currency                CHAR(3)   NOT NULL DEFAULT 'PEN',
    concept                 VARCHAR(100),
    description             TEXT,

    -- Programación
    frequency               yape.pay_frequency NOT NULL,
    day_of_month            SMALLINT,    -- 1-28 (evitamos 29-31 para compatibilidad mensual)
    day_of_week             SMALLINT,    -- 1=lunes … 7=domingo
    next_payment_date       TIMESTAMPTZ NOT NULL,
    last_payment_date       TIMESTAMPTZ,
    start_date              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    end_date                TIMESTAMPTZ,

    -- Auto pago
    auto_pay_enabled        BOOLEAN   NOT NULL DEFAULT FALSE,
    failure_retry_count     SMALLINT  NOT NULL DEFAULT 0,
    times_executed          INTEGER   NOT NULL DEFAULT 0,

    -- Notificaciones
    notify_days_in_advance  SMALLINT  NOT NULL DEFAULT 1,

    status                  yape.scheduled_status NOT NULL DEFAULT 'ACTIVE',
    paused_at               TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_scheduled_amount_positive CHECK (amount > 0.00),
    CONSTRAINT ck_day_of_month_range        CHECK (day_of_month IS NULL OR day_of_month BETWEEN 1 AND 28),
    CONSTRAINT ck_day_of_week_range         CHECK (day_of_week  IS NULL OR day_of_week  BETWEEN 1 AND 7)
);

CREATE INDEX idx_scheduled_wallet_from  ON yape.scheduled_payments (wallet_from_id);
CREATE INDEX idx_scheduled_status       ON yape.scheduled_payments (status);
-- El job nocturno consulta por pagos que vencen hoy
CREATE INDEX idx_scheduled_next_date    ON yape.scheduled_payments (next_payment_date)
    WHERE status = 'ACTIVE';

-- ============================================================
-- 10. TRANSACTION RECEIPTS — Feature #6: Comprobantes bonitos
-- ============================================================
CREATE TABLE yape.transaction_receipts (
    id               UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id   UUID     NOT NULL UNIQUE REFERENCES yape.transactions(id),

    receipt_number   VARCHAR(50) NOT NULL,
    business_name    VARCHAR(255),
    business_ruc     VARCHAR(11),
    customer_name    VARCHAR(255),

    amount           NUMERIC(14,2) NOT NULL,
    currency         CHAR(3),
    concept          VARCHAR(255),
    description      TEXT,

    receipt_html     TEXT,
    receipt_pdf_url  TEXT,
    qr_code_url      TEXT,      -- QR para validar autenticidad del comprobante

    printed_count    INTEGER   NOT NULL DEFAULT 0,
    emailed_to       VARCHAR(255),
    sent_whatsapp    BOOLEAN   NOT NULL DEFAULT FALSE,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_receipt_number UNIQUE (receipt_number)
);

CREATE INDEX idx_receipts_transaction ON yape.transaction_receipts (transaction_id);
CREATE INDEX idx_receipts_business    ON yape.transaction_receipts (business_ruc) WHERE business_ruc IS NOT NULL;

-- ============================================================
-- 11. EXPENSE ANALYTICS — Feature #10: Ranking de gastos
-- ============================================================
CREATE TABLE yape.expense_categories (
    id            UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID     NOT NULL REFERENCES yape.users(id),
    category_name VARCHAR(100) NOT NULL,
    icon_code     VARCHAR(50),   -- nombre del icono en la app Android
    color_hex     CHAR(7),       -- #RRGGBB para UI

    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_expense_category_user UNIQUE (user_id, category_name)
);

-- Snapshot mensual de gastos por categoría (evita recalcular siempre)
CREATE TABLE yape.expense_analytics (
    id                UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id         UUID     NOT NULL REFERENCES yape.wallets(id),
    category_id       UUID     REFERENCES yape.expense_categories(id),
    year_month        CHAR(7)  NOT NULL,        -- "2024-01"

    total_spent       NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    transaction_count INTEGER       NOT NULL DEFAULT 0,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_analytics_wallet_cat_month UNIQUE (wallet_id, category_id, year_month)
);

CREATE INDEX idx_analytics_wallet     ON yape.expense_analytics (wallet_id);
CREATE INDEX idx_analytics_year_month ON yape.expense_analytics (year_month);

-- ============================================================
-- 12. LOANS — Feature #11: Préstamos transparentes
-- ============================================================
CREATE TABLE yape.loans (
    id                     UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    borrower_user_id       UUID     NOT NULL REFERENCES yape.users(id),
    lender_user_id         UUID     NOT NULL REFERENCES yape.users(id),
    transaction_id         UUID     REFERENCES yape.transactions(id),

    original_amount        NUMERIC(14,2) NOT NULL,
    remaining_balance      NUMERIC(14,2) NOT NULL,
    interest_rate          NUMERIC(5,2)  NOT NULL DEFAULT 0.00,  -- % mensual
    total_amount_to_return NUMERIC(14,2) NOT NULL,               -- Crystal-clear Feature #11
    late_fee_per_day       NUMERIC(14,2) NOT NULL DEFAULT 0.00,

    loan_status            yape.loan_status NOT NULL DEFAULT 'ACTIVE',
    loan_date              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    due_date               TIMESTAMPTZ,
    completed_date         TIMESTAMPTZ,

    notes                  TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_loan_different_users   CHECK (borrower_user_id <> lender_user_id),
    CONSTRAINT ck_loan_amount_positive   CHECK (original_amount > 0.00),
    CONSTRAINT ck_interest_rate_ok       CHECK (interest_rate BETWEEN 0.00 AND 100.00),
    CONSTRAINT ck_total_gte_original     CHECK (total_amount_to_return >= original_amount)
);

CREATE INDEX idx_loans_borrower ON yape.loans (borrower_user_id);
CREATE INDEX idx_loans_lender   ON yape.loans (lender_user_id);
CREATE INDEX idx_loans_status   ON yape.loans (loan_status);

-- ============================================================
-- 13. NOTIFICATIONS
-- ============================================================
CREATE TABLE yape.notifications (
    id                UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID     NOT NULL REFERENCES yape.users(id),
    title             VARCHAR(255),
    message           TEXT     NOT NULL,
    notification_type yape.notif_type NOT NULL,
    related_entity_id UUID,           -- ID genérico de la entidad relacionada

    is_read           BOOLEAN  NOT NULL DEFAULT FALSE,
    read_at           TIMESTAMPTZ,
    sent_via          VARCHAR(20),    -- PUSH, SMS, EMAIL, IN_APP

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMPTZ
);

CREATE INDEX idx_notif_user       ON yape.notifications (user_id);
CREATE INDEX idx_notif_unread     ON yape.notifications (user_id, is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notif_created_at ON yape.notifications (created_at DESC);

-- ============================================================
-- TRIGGER: updated_at automático en todas las tablas
-- ============================================================
CREATE OR REPLACE FUNCTION yape.set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'users','wallets','qr_codes','transactions',
        'business_profiles','inventory_items','disputes',
        'groups','group_members','scheduled_payments',
        'transaction_receipts','expense_analytics','loans','notifications'
    ]
    LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_updated_at
             BEFORE UPDATE ON yape.%s
             FOR EACH ROW EXECUTE FUNCTION yape.set_updated_at();',
            t, t
        );
    END LOOP;
END;
$$;
