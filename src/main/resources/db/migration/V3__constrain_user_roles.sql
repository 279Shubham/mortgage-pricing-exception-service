ALTER TABLE app_user_role ADD CONSTRAINT app_user_role_role_check
    CHECK (role IN ('RM', 'REVIEWER', 'APPLICATION_PROCESS'));
