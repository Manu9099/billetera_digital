ALTER TABLE yape.wallets
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE yape.transactions
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE yape.qr_codes
    ALTER COLUMN currency TYPE VARCHAR(3);