ALTER TABLE request_revision ADD COLUMN advice_recommendation VARCHAR(20);
ALTER TABLE request_revision ADD COLUMN advice_rationale VARCHAR(500);

UPDATE request_revision
    SET advice_recommendation = 'REVIEW_CAREFULLY',
        advice_rationale = 'No advice was recorded when this revision was created.'
    WHERE advice_recommendation IS NULL;

ALTER TABLE request_revision ALTER COLUMN advice_recommendation SET NOT NULL;
ALTER TABLE request_revision ALTER COLUMN advice_rationale SET NOT NULL;
