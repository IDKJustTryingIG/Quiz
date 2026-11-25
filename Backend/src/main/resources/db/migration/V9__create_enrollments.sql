-- A user's place in a track. Day 1 is start_date; the day number then moves
-- on with the calendar, whether or not the user takes that day's quiz.
--
-- Rows are kept after a user leaves or finishes, as history. A user may
-- re-enroll in the same track later, so (user_id, track_id) is not unique.
CREATE TABLE enrollments (
                             enrollment_id BIGSERIAL PRIMARY KEY,
                             user_id BIGINT NOT NULL,
                             track_id BIGINT NOT NULL,
                             start_date DATE NOT NULL,
                             status VARCHAR(20) NOT NULL,
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             ended_at TIMESTAMP,

                             CONSTRAINT fk_enrollments_user
                                 FOREIGN KEY (user_id)
                                     REFERENCES users(user_id)
                                     ON DELETE CASCADE,

                             CONSTRAINT fk_enrollments_track
                                 FOREIGN KEY (track_id)
                                     REFERENCES tracks(track_id)
                                     ON DELETE CASCADE,

                             CONSTRAINT chk_enrollments_status
                                 CHECK (status IN ('ACTIVE', 'COMPLETED', 'LEFT'))
);

-- One active track per user. Two enroll requests at once cannot both succeed.
CREATE UNIQUE INDEX uq_enrollments_one_active_per_user
    ON enrollments (user_id)
    WHERE status = 'ACTIVE';

-- The daily jobs read every active enrollment of a track.
CREATE INDEX idx_enrollments_active_track
    ON enrollments (track_id)
    WHERE status = 'ACTIVE';
