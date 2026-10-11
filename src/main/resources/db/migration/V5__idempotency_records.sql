CREATE TABLE idempotency_record (
    id                UUID                        PRIMARY KEY,
    user_id           VARCHAR(40)                 NOT NULL,
    route             VARCHAR(200)                NOT NULL,
    idempotency_key   VARCHAR(255)                NOT NULL,
    fingerprint       VARCHAR(64)                 NOT NULL,
    response_status   INTEGER,
    response_location VARCHAR(200),
    response_body     VARCHAR(10000),
    expires_at        TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    CONSTRAINT idempotency_key_per_user_and_route UNIQUE (user_id, route, idempotency_key)
);
