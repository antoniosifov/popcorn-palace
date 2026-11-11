-- Signing in with Google.
--
-- Google names an account by a "subject", a number that stays the same when
-- the person changes their email address there. That, not the email, is what
-- an account here is connected by.
ALTER TABLE users ADD COLUMN google_subject VARCHAR(255);
ALTER TABLE users ADD CONSTRAINT uq_users_google_subject UNIQUE (google_subject);

-- Whether the person behind the account has shown that the email is theirs:
-- by opening a link sent to it (a password reset) or by signing in with Google,
-- which vouches for it. Registration with a password asks for no proof, so
-- this starts as false.
--
-- It matters when a Google sign-in meets an account that already uses its
-- email. If nobody ever proved that account's email, its password may have
-- been set by a stranger who got there first, and must not survive the
-- rightful owner arriving (see GoogleSignInService).
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
