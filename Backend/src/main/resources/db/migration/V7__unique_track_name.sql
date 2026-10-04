-- Track names are unique across all users, compared exactly (case-sensitive).
ALTER TABLE tracks
    ADD CONSTRAINT uq_tracks_name UNIQUE (name);
