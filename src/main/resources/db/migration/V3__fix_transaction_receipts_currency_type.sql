ALTER TABLE yape.transaction_receipts
    ALTER COLUMN currency TYPE VARCHAR(3)
    USING TRIM(currency)::VARCHAR(3);

ALTER TABLE yape.transaction_receipts
    ALTER COLUMN currency SET NOT NULL;