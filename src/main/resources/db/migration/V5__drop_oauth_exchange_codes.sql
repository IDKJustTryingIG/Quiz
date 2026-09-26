-- The Google redirect now sets the refresh cookie directly on the 302, and the frontend
-- calls /api/auth/refresh to obtain an access token. Nothing sensitive travels in the
-- URL any more, so the single-use handoff code -- and this table -- are unnecessary.
DROP TABLE IF EXISTS oauth_exchange_codes;
