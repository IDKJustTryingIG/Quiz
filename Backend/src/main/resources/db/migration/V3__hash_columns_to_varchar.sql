-- V2 declared the hash columns as CHAR(64). Postgres stores CHAR as bpchar, while the
-- entities map String + @Column(length = 64) to varchar(64), so ddl-auto=validate
-- refused to start.
--
-- Fixed forward rather than by editing V2: V2 has already been applied, and changing
-- an applied migration breaks Flyway's checksum validation.
--
-- CHAR is also the wrong type on its own merits: it blank-pads to the declared width
-- and ignores trailing spaces when comparing, neither of which is wanted for a hash.
ALTER TABLE refresh_tokens       ALTER COLUMN token_hash TYPE VARCHAR(64);
ALTER TABLE oauth_exchange_codes ALTER COLUMN code_hash  TYPE VARCHAR(64);
