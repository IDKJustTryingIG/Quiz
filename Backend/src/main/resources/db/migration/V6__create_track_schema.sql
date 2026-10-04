CREATE TABLE tracks (
                        track_id BIGSERIAL PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        slug VARCHAR(255) NOT NULL UNIQUE,
                        description TEXT,
                        visibility VARCHAR(20) NOT NULL,
                        owner_id BIGINT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        CONSTRAINT fk_tracks_owner
                            FOREIGN KEY (owner_id)
                                REFERENCES users(user_id),

                        CONSTRAINT chk_tracks_visibility
                            CHECK (visibility IN ('PUBLIC', 'PRIVATE'))
);


CREATE TABLE topics (
                        topic_id BIGSERIAL PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        slug VARCHAR(255) NOT NULL UNIQUE,
                        description TEXT,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE track_topics (
                              track_topic_id BIGSERIAL PRIMARY KEY,
                              track_id BIGINT NOT NULL,
                              topic_id BIGINT NOT NULL,
                              position INTEGER NOT NULL,

                              CONSTRAINT fk_track_topics_track
                                  FOREIGN KEY (track_id)
                                      REFERENCES tracks(track_id)
                                      ON DELETE CASCADE,

                              CONSTRAINT fk_track_topics_topic
                                  FOREIGN KEY (topic_id)
                                      REFERENCES topics(topic_id)
                                      ON DELETE CASCADE,

                              CONSTRAINT uq_track_topic
                                  UNIQUE (track_id, topic_id),

                              CONSTRAINT uq_track_topic_position
                                  UNIQUE (track_id, position)
);


CREATE TABLE files (
                       file_id BIGSERIAL PRIMARY KEY,
                       name VARCHAR(255) NOT NULL,
                       original_filename VARCHAR(255) NOT NULL,
                       storage_key VARCHAR(1000) NOT NULL UNIQUE,
                       content_type VARCHAR(100) NOT NULL,
                       size_bytes BIGINT NOT NULL,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE topic_files (
                             topic_file_id BIGSERIAL PRIMARY KEY,
                             topic_id BIGINT NOT NULL,
                             file_id BIGINT NOT NULL,
                             position INTEGER NOT NULL,

                             CONSTRAINT fk_topic_files_topic
                                 FOREIGN KEY (topic_id)
                                     REFERENCES topics(topic_id)
                                     ON DELETE CASCADE,

                             CONSTRAINT fk_topic_files_file
                                 FOREIGN KEY (file_id)
                                     REFERENCES files(file_id)
                                     ON DELETE CASCADE,

                             CONSTRAINT uq_topic_file
                                 UNIQUE (topic_id, file_id),

                             CONSTRAINT uq_topic_file_position
                                 UNIQUE (topic_id, position)
);