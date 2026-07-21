ALTER TABLE queue_sessions
    DROP CONSTRAINT ck_queue_sessions_operating_time_range;

ALTER TABLE queue_sessions
    ADD CONSTRAINT ck_queue_sessions_operating_time_range CHECK (
        closing_time IS NULL OR closing_time <> opening_time
    );
