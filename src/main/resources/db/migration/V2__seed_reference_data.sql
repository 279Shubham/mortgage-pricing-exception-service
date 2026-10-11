INSERT INTO app_user (id, display_name) VALUES
    ('rm-anna', 'Anna (Relationship Manager)'),
    ('rm-carl', 'Carl (Relationship Manager)'),
    ('reviewer-ben', 'Ben (Reviewer)'),
    ('reviewer-dana', 'Dana (Reviewer)'),
    ('dual-erin', 'Erin (RM + Reviewer, four-eyes demo)'),
    ('application-process', 'Mortgage application process (service user)');

INSERT INTO app_user_role (user_id, role) VALUES
    ('rm-anna', 'RM'),
    ('rm-carl', 'RM'),
    ('reviewer-ben', 'REVIEWER'),
    ('reviewer-dana', 'REVIEWER'),
    ('dual-erin', 'RM'),
    ('dual-erin', 'REVIEWER'),
    ('application-process', 'APPLICATION_PROCESS');

INSERT INTO mortgage_application (id, borrower_label, standard_rate_bps) VALUES
    ('MA-1001', 'Fictional household A', 425),
    ('MA-1002', 'Fictional household B', 380),
    ('MA-1003', 'Fictional household C', 495),
    ('MA-1004', 'Fictional household D', 520),
    ('MA-1005', 'Fictional household E', 355),
    ('MA-1006', 'Fictional household F (low rate)', 150);
