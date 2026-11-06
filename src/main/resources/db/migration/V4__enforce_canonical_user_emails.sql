-- V4: Make user emails canonical (trimmed + lowercase) and keep them that way.
--
-- Email lookups are exact-match against a case-sensitive UNIQUE column, so
-- "Ada@x.com" and "ada@x.com" could exist as two accounts, and a user who
-- signed up as "Ada@x.com" could not log in typing "ada@x.com".
--
-- The application now normalizes every email before it reads or writes one.
-- This CHECK is the database-level guarantee behind that: a non-canonical
-- email can never be stored, whichever code path tries to write it.
--
-- The UPDATE below fails on the UNIQUE constraint if two existing accounts
-- differ only by case or padding. That is deliberate: merging accounts is a
-- human decision, so audit before deploying:
--   SELECT lower(btrim(email)) FROM users WHERE email IS NOT NULL
--   GROUP BY 1 HAVING count(*) > 1;

UPDATE users
   SET email = lower(btrim(email))
 WHERE email IS NOT NULL
   AND email <> lower(btrim(email));

ALTER TABLE users
    ADD CONSTRAINT users_email_canonical
    CHECK (email IS NULL OR email = lower(btrim(email)));
