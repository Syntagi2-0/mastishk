ALTER TABLE queue_sessions
    ADD COLUMN opening_time TIME,
    ADD COLUMN closing_time TIME;

UPDATE queue_sessions qs
SET opening_time = COALESCE(ss.operating_start_time, qs.opened_at::time),
    closing_time = ss.operating_end_time
FROM service_schedules ss
WHERE qs.service_schedule_id = ss.id;

UPDATE queue_sessions
SET opening_time = opened_at::time
WHERE opening_time IS NULL;

ALTER TABLE queue_sessions
    ALTER COLUMN opening_time SET NOT NULL,
    ADD CONSTRAINT ck_queue_sessions_operating_time_range CHECK (
        closing_time IS NULL OR closing_time > opening_time
    );
