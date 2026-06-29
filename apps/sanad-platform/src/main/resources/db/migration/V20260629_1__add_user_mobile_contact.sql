ALTER TABLE users
    ADD COLUMN IF NOT EXISTS mobile_number VARCHAR(20),
    ADD COLUMN IF NOT EXISTS mobile_region VARCHAR(2);

ALTER TABLE users
    ADD CONSTRAINT ck_users_mobile_number_e164
        CHECK (mobile_number IS NULL OR mobile_number ~ '^\+[1-9][0-9]{7,14}$'),
    ADD CONSTRAINT ck_users_mobile_region_iso2
        CHECK (mobile_region IS NULL OR mobile_region ~ '^[A-Z]{2}$');
