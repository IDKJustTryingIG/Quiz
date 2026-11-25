-- Daily email settings of each enrollment. The token is the secret in the
-- "Stop daily emails" link, so the link works without signing in.
ALTER TABLE enrollments ADD COLUMN email_opt_in BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE enrollments ADD COLUMN unsubscribe_token VARCHAR(64);
UPDATE enrollments SET unsubscribe_token = replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');
ALTER TABLE enrollments ALTER COLUMN unsubscribe_token SET NOT NULL;
ALTER TABLE enrollments ADD CONSTRAINT uq_enrollments_unsubscribe_token UNIQUE (unsubscribe_token);

-- One row per email to send. The 06:00 run adds the day's rows, then a
-- sender sends them one at a time. The unique key stops a second run, or a
-- restart, from sending the same email twice.
CREATE TABLE email_deliveries (
                                  delivery_id BIGSERIAL PRIMARY KEY,
                                  enrollment_id BIGINT NOT NULL,
                                  send_date DATE NOT NULL,
                                  kind VARCHAR(20) NOT NULL,
                                  status VARCHAR(20) NOT NULL,
                                  attempts INTEGER NOT NULL DEFAULT 0,
                                  next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                  -- The id Resend gave the email.
                                  provider_id VARCHAR(100),
                                  last_error TEXT,
                                  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  sent_at TIMESTAMP WITH TIME ZONE,

                                  CONSTRAINT fk_email_deliveries_enrollment
                                      FOREIGN KEY (enrollment_id)
                                          REFERENCES enrollments(enrollment_id)
                                          ON DELETE CASCADE,

                                  CONSTRAINT uq_email_deliveries_once
                                      UNIQUE (enrollment_id, send_date, kind),

                                  CONSTRAINT chk_email_deliveries_kind
                                      CHECK (kind IN ('DAILY')),

                                  CONSTRAINT chk_email_deliveries_status
                                      CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_email_deliveries_due
    ON email_deliveries (next_attempt_at)
    WHERE status = 'PENDING';
