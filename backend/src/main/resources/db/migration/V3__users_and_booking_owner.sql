-- Accounts. Until now a booking carried whatever user id the client sent;
-- from here on it belongs to a registered user.

CREATE TABLE users
(
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    -- Emails are stored lowercased, so the unique constraint is case-insensitive.
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

-- Bookings made before accounts existed keep their user ids. Each of those ids
-- gets a placeholder account so the foreign key below holds. '!' is not a valid
-- password hash, so nobody can log in to a placeholder account.
INSERT INTO users (id, email, password_hash, display_name, role, created_at)
SELECT user_id,
       'legacy-' || user_id || '@placeholder.invalid',
       '!',
       'Legacy customer',
       'CUSTOMER',
       min(created_at)
FROM bookings
GROUP BY user_id;

ALTER TABLE bookings
    ADD CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id);
