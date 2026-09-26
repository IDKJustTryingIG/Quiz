-- user_agent and client_ip existed for a "your active sessions" view. Single-device
-- login makes that view meaningless: at most one session is ever live per user.
--
-- client_ip was also wrong in any deployment behind a proxy, where getRemoteAddr()
-- records the proxy's address rather than the client's, and IP addresses carry data
-- protection obligations that a never-queried column does not justify.
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS user_agent;
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS client_ip;
