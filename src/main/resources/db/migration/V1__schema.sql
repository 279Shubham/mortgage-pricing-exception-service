CREATE TABLE app_user (
    id           VARCHAR(40)  PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL
);

CREATE TABLE app_user_role (
    user_id VARCHAR(40) NOT NULL REFERENCES app_user (id),
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE mortgage_application (
    id                VARCHAR(20)  PRIMARY KEY,
    borrower_label    VARCHAR(100) NOT NULL,
    standard_rate_bps INTEGER      NOT NULL CHECK (standard_rate_bps > 0)
);
