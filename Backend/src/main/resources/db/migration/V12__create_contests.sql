-- The 18:00 contest: one per track per date. Every enrolled user on a day of
-- the track takes part. Each day group answers its own quiz, and all share
-- one ranking.
--
-- The state comes from the clock: before opens_at the contest is upcoming,
-- until closes_at it is open, then it is closed. scored_at records when the
-- server scored the attempts that nobody submitted.
CREATE TABLE quiz_sessions (
                               session_id BIGSERIAL PRIMARY KEY,
                               track_id BIGINT NOT NULL,
                               session_date DATE NOT NULL,
                               opens_at TIMESTAMP WITH TIME ZONE NOT NULL,
                               -- The last moment a user can start.
                               start_deadline TIMESTAMP WITH TIME ZONE NOT NULL,
                               closes_at TIMESTAMP WITH TIME ZONE NOT NULL,
                               scored_at TIMESTAMP WITH TIME ZONE,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_quiz_sessions_track
                                   FOREIGN KEY (track_id)
                                       REFERENCES tracks(track_id)
                                       ON DELETE CASCADE,

                               CONSTRAINT uq_quiz_sessions_track_date
                                   UNIQUE (track_id, session_date)
);

-- One day group of a contest, and the quiz it answers. quiz_id is pinned
-- before the contest opens and never changes after.
CREATE TABLE session_days (
                              session_day_id BIGSERIAL PRIMARY KEY,
                              session_id BIGINT NOT NULL,
                              day_number INTEGER NOT NULL,
                              -- Null if the owner removed the day later. Past results stay.
                              topic_id BIGINT,
                              quiz_id BIGINT,

                              CONSTRAINT fk_session_days_session
                                  FOREIGN KEY (session_id)
                                      REFERENCES quiz_sessions(session_id)
                                      ON DELETE CASCADE,

                              CONSTRAINT fk_session_days_topic
                                  FOREIGN KEY (topic_id)
                                      REFERENCES topics(topic_id)
                                      ON DELETE SET NULL,

                              CONSTRAINT fk_session_days_quiz
                                  FOREIGN KEY (quiz_id)
                                      REFERENCES quizzes(quiz_id)
                                      ON DELETE SET NULL,

                              CONSTRAINT uq_session_days_day
                                  UNIQUE (session_id, day_number)
);

CREATE INDEX idx_session_days_quiz ON session_days (quiz_id);

-- One user's go at a contest. One per user per contest.
CREATE TABLE quiz_attempts (
                               attempt_id BIGSERIAL PRIMARY KEY,
                               session_id BIGINT NOT NULL,
                               user_id BIGINT NOT NULL,
                               day_number INTEGER NOT NULL,
                               quiz_id BIGINT,
                               status VARCHAR(20) NOT NULL,
                               started_at TIMESTAMP WITH TIME ZONE NOT NULL,
                               -- The time limit: 10 minutes from the start, never after closes_at.
                               deadline_at TIMESTAMP WITH TIME ZONE NOT NULL,
                               submitted_at TIMESTAMP WITH TIME ZONE,
                               score INTEGER,
                               total INTEGER NOT NULL,
                               duration_ms BIGINT,

                               CONSTRAINT fk_quiz_attempts_session
                                   FOREIGN KEY (session_id)
                                       REFERENCES quiz_sessions(session_id)
                                       ON DELETE CASCADE,

                               CONSTRAINT fk_quiz_attempts_user
                                   FOREIGN KEY (user_id)
                                       REFERENCES users(user_id)
                                       ON DELETE CASCADE,

                               CONSTRAINT fk_quiz_attempts_quiz
                                   FOREIGN KEY (quiz_id)
                                       REFERENCES quizzes(quiz_id)
                                       ON DELETE SET NULL,

                               CONSTRAINT uq_quiz_attempts_one_per_user
                                   UNIQUE (session_id, user_id),

                               CONSTRAINT chk_quiz_attempts_status
                                   CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED'))
);

CREATE INDEX idx_quiz_attempts_quiz ON quiz_attempts (quiz_id);

-- One answer of an attempt. The first answer to a question is final.
CREATE TABLE quiz_answers (
                              answer_id BIGSERIAL PRIMARY KEY,
                              attempt_id BIGINT NOT NULL,
                              quiz_question_id BIGINT NOT NULL,
                              chosen_index SMALLINT NOT NULL,
                              -- Null until the attempt is scored.
                              correct BOOLEAN,
                              answered_at TIMESTAMP WITH TIME ZONE NOT NULL,

                              CONSTRAINT fk_quiz_answers_attempt
                                  FOREIGN KEY (attempt_id)
                                      REFERENCES quiz_attempts(attempt_id)
                                      ON DELETE CASCADE,

                              CONSTRAINT fk_quiz_answers_question
                                  FOREIGN KEY (quiz_question_id)
                                      REFERENCES quiz_questions(quiz_question_id)
                                      ON DELETE CASCADE,

                              CONSTRAINT uq_quiz_answers_question
                                  UNIQUE (attempt_id, quiz_question_id),

                              CONSTRAINT chk_quiz_answers_chosen_index
                                  CHECK (chosen_index BETWEEN 0 AND 3)
);
