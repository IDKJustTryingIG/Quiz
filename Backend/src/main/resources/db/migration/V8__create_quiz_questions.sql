-- The cached quiz of each topic: one row per question, in quiz order.
--
-- A cached quiz is valid only for the files it was made from (source_hash) and
-- for the prompt, model and question count that made it (generator_version).
-- When either changes, the server makes a new quiz and deletes the old rows.
CREATE TABLE quiz_questions (
                                quiz_question_id BIGSERIAL PRIMARY KEY,
                                topic_id BIGINT NOT NULL,
                                source_hash VARCHAR(64) NOT NULL,
                                generator_version VARCHAR(100) NOT NULL,
                                position INTEGER NOT NULL,
                                question TEXT NOT NULL,
                                option_a TEXT NOT NULL,
                                option_b TEXT NOT NULL,
                                option_c TEXT NOT NULL,
                                option_d TEXT NOT NULL,
                                correct_index SMALLINT NOT NULL,
                                explanation TEXT NOT NULL,
                                source_file VARCHAR(255),
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT fk_quiz_questions_topic
                                    FOREIGN KEY (topic_id)
                                        REFERENCES topics(topic_id)
                                        ON DELETE CASCADE,

                                -- Two requests that make the same quiz at once cannot both save it.
                                CONSTRAINT uq_quiz_question_position
                                    UNIQUE (topic_id, source_hash, generator_version, position),

                                CONSTRAINT chk_quiz_questions_correct_index
                                    CHECK (correct_index BETWEEN 0 AND 3)
);
