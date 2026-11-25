-- The queue of quizzes to make. The planner adds one row per topic that an
-- enrolled user needs on run_date. Workers take PENDING rows one at a time
-- (FOR UPDATE SKIP LOCKED), ask the model, and mark the row DONE or FAILED.
--
-- The rows stay after the day, so the admin page can show what happened.
CREATE TABLE quiz_generation_jobs (
                                      job_id BIGSERIAL PRIMARY KEY,
                                      -- The topic is read through one of its tracks. Any track that has it works.
                                      track_id BIGINT NOT NULL,
                                      topic_id BIGINT NOT NULL,
                                      run_date DATE NOT NULL,
                                      status VARCHAR(20) NOT NULL,
                                      attempts INTEGER NOT NULL DEFAULT 0,
                                      next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                      locked_at TIMESTAMP WITH TIME ZONE,
                                      last_error TEXT,
                                      quiz_id BIGINT,
                                      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      finished_at TIMESTAMP WITH TIME ZONE,

                                      CONSTRAINT fk_quiz_jobs_track
                                          FOREIGN KEY (track_id)
                                              REFERENCES tracks(track_id)
                                              ON DELETE CASCADE,

                                      CONSTRAINT fk_quiz_jobs_topic
                                          FOREIGN KEY (topic_id)
                                              REFERENCES topics(topic_id)
                                              ON DELETE CASCADE,

                                      CONSTRAINT fk_quiz_jobs_quiz
                                          FOREIGN KEY (quiz_id)
                                              REFERENCES quizzes(quiz_id)
                                              ON DELETE SET NULL,

                                      -- One job per topic per day, however many users need it.
                                      CONSTRAINT uq_quiz_jobs_topic_date
                                          UNIQUE (topic_id, run_date),

                                      CONSTRAINT chk_quiz_jobs_status
                                          CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED'))
);

-- Workers look for PENDING rows whose delay is over.
CREATE INDEX idx_quiz_jobs_claimable
    ON quiz_generation_jobs (next_attempt_at)
    WHERE status = 'PENDING';
