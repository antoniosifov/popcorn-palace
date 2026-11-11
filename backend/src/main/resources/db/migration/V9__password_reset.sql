-- A forgotten password is replaced through a link sent to the account's email.
--
-- The link carries a random token. Only its SHA-256 is stored here: whoever
-- reads this table (a backup, a mistaken query, an attacker) finds nothing
-- they could put into a link. The token is long and random, so, unlike a
-- password, a plain fast hash is enough: there is nothing to guess.
CREATE TABLE password_reset_tokens
(
    -- SHA-256 of the token, in hexadecimal.
    token_hash CHAR(64)    NOT NULL PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    -- One link per account: asking again replaces the earlier one, so only the
    -- newest email works.
    CONSTRAINT uq_password_reset_tokens_user UNIQUE (user_id)
);

-- Access tokens are verified by their signature alone, so one that was issued
-- before the password changed would keep working until it expires. Each token
-- now says which "version" of the account's sessions it belongs to; changing
-- the password moves the account to the next version and the older tokens are
-- refused (see SessionVersions).
ALTER TABLE users ADD COLUMN session_version INTEGER NOT NULL DEFAULT 0;

-- An event that carries something secret (the link above) must not stay in
-- the outbox after it has been sent: published events are otherwise kept for
-- a week for troubleshooting. A confidential one is deleted the moment Kafka
-- has acknowledged it.
ALTER TABLE outbox_events ADD COLUMN confidential BOOLEAN NOT NULL DEFAULT FALSE;
