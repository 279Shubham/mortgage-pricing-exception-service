ALTER TABLE pricing_exception_request ADD COLUMN decided_by VARCHAR(40) REFERENCES app_user (id);
ALTER TABLE pricing_exception_request ADD COLUMN decided_at TIMESTAMP(3) WITH TIME ZONE;
ALTER TABLE pricing_exception_request ADD COLUMN decided_revision INTEGER;
ALTER TABLE pricing_exception_request ADD COLUMN decision_note VARCHAR(1000);
