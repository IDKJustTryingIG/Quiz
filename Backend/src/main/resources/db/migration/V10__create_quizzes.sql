-- One row per generated quiz. Its questions point at it, so later tables
-- (contests, attempts) can refer to one exact quiz.
--
-- A quiz is valid only for the files it was made from (source_hash) and for
-- the prompt, model and question count that made it (generator_version).
CREATE TABLE quizzes (
                         quiz_id BIGSERIAL PRIMARY KEY,
                         topic_id BIGINT NOT NULL,
                         source_hash VARCHAR(64) NOT NULL,
                         generator_version VARCHAR(100) NOT NULL,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         CONSTRAINT fk_quizzes_topic
                             FOREIGN KEY (topic_id)
                                 REFERENCES topics(topic_id)
                                 ON DELETE CASCADE,

                         -- Two requests that make the same quiz at once cannot both save it.
                         CONSTRAINT uq_quizzes_version
                             UNIQUE (topic_id, source_hash, generator_version)
);

-- Keep the quizzes cached by V8: each (topic, hash, version) group is one quiz.
INSERT INTO quizzes (topic_id, source_hash, generator_version, created_at)
SELECT topic_id, source_hash, generator_version, MIN(created_at)
FROM quiz_questions
GROUP BY topic_id, source_hash, generator_version;

ALTER TABLE quiz_questions ADD COLUMN quiz_id BIGINT;

UPDATE quiz_questions q
SET quiz_id = z.quiz_id
FROM quizzes z
WHERE z.topic_id = q.topic_id
  AND z.source_hash = q.source_hash
  AND z.generator_version = q.generator_version;

ALTER TABLE quiz_questions ALTER COLUMN quiz_id SET NOT NULL;

ALTER TABLE quiz_questions
    ADD CONSTRAINT fk_quiz_questions_quiz
        FOREIGN KEY (quiz_id)
            REFERENCES quizzes(quiz_id)
            ON DELETE CASCADE;

-- The quiz row now holds these.
ALTER TABLE quiz_questions DROP CONSTRAINT uq_quiz_question_position;
ALTER TABLE quiz_questions DROP CONSTRAINT fk_quiz_questions_topic;
ALTER TABLE quiz_questions DROP COLUMN topic_id;
ALTER TABLE quiz_questions DROP COLUMN source_hash;
ALTER TABLE quiz_questions DROP COLUMN generator_version;

ALTER TABLE quiz_questions
    ADD CONSTRAINT uq_quiz_question_position
        UNIQUE (quiz_id, position);
