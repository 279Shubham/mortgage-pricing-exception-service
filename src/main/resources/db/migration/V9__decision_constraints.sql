ALTER TABLE pricing_exception_request ADD CONSTRAINT decision_is_complete CHECK (
    (status IN ('APPROVED', 'DECLINED')) = (decided_by IS NOT NULL)
    AND (decided_by IS NULL) = (decided_at IS NULL)
    AND (decided_by IS NULL) = (decided_revision IS NULL));

ALTER TABLE pricing_exception_request ADD CONSTRAINT decided_by_another_user
    CHECK (decided_by <> requested_by);

ALTER TABLE pricing_exception_request ADD CONSTRAINT decided_revision_exists
    FOREIGN KEY (id, decided_revision) REFERENCES request_revision (request_id, revision_number);

ALTER TABLE request_revision ADD CONSTRAINT advice_recommendation_check
    CHECK (advice_recommendation IN ('APPROVE', 'REVIEW_CAREFULLY'));
