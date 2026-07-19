WITH ranked_active_sessions AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY queue_id
               ORDER BY business_date DESC, created_at DESC, id DESC
           ) AS active_rank
    FROM queue_sessions
    WHERE status IN ('OPEN', 'PAUSED')
)
UPDATE queue_sessions
SET status = 'CLOSED',
    closed_at = COALESCE(closed_at, now()),
    updated_at = now()
WHERE id IN (
    SELECT id
    FROM ranked_active_sessions
    WHERE active_rank > 1
);

CREATE UNIQUE INDEX uq_queue_sessions_single_open_or_paused
    ON queue_sessions (queue_id)
    WHERE status IN ('OPEN', 'PAUSED');
