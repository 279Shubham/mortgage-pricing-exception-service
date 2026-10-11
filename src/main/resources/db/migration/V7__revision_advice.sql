ALTER TABLE request_revision ADD COLUMN advice_recommendation VARCHAR(20) NOT NULL;
ALTER TABLE request_revision ADD COLUMN advice_rationale VARCHAR(500) NOT NULL;
