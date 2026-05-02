ALTER TABLE yape.expense_categories
    ALTER COLUMN color_hex TYPE VARCHAR(7)
    USING TRIM(color_hex)::VARCHAR(7);

ALTER TABLE yape.expense_analytics
    ALTER COLUMN year_month TYPE VARCHAR(7)
    USING TRIM(year_month)::VARCHAR(7);