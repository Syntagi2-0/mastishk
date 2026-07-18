CREATE TABLE queues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    business_service_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_queues_business FOREIGN KEY (business_id)
        REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_queues_service FOREIGN KEY (business_service_id)
        REFERENCES business_services (id) ON DELETE RESTRICT,
    CONSTRAINT uq_queues_business_service UNIQUE (business_id, business_service_id),
    CONSTRAINT ck_queues_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'CLOSED', 'ARCHIVED')
    )
);

INSERT INTO queues (
    business_id,
    business_service_id,
    name,
    status,
    created_at,
    updated_at
)
SELECT
    qs.business_id,
    qs.business_service_id,
    bs.name,
    'ACTIVE',
    MIN(qs.created_at),
    MAX(qs.updated_at)
FROM queue_sessions qs
JOIN business_services bs ON bs.id = qs.business_service_id
GROUP BY qs.business_id, qs.business_service_id, bs.name;

ALTER TABLE queue_sessions ADD COLUMN queue_id UUID;

UPDATE queue_sessions qs
SET queue_id = q.id
FROM queues q
WHERE q.business_id = qs.business_id
  AND q.business_service_id = qs.business_service_id;

ALTER TABLE queue_sessions
    ALTER COLUMN queue_id SET NOT NULL,
    ADD CONSTRAINT fk_queue_sessions_queue FOREIGN KEY (queue_id)
        REFERENCES queues (id) ON DELETE RESTRICT,
    DROP CONSTRAINT uq_queue_sessions_service_date,
    DROP CONSTRAINT ck_queue_sessions_status,
    DROP CONSTRAINT ck_queue_sessions_closed_state,
    ADD CONSTRAINT uq_queue_sessions_queue_date UNIQUE (queue_id, business_date),
    ADD CONSTRAINT ck_queue_sessions_status CHECK (
        status IN ('CREATED', 'OPEN', 'PAUSED', 'CLOSED', 'ARCHIVED')
    ),
    ADD CONSTRAINT ck_queue_sessions_closed_state CHECK (
        (status IN ('CREATED', 'OPEN', 'PAUSED') AND closed_at IS NULL)
        OR (status IN ('CLOSED', 'ARCHIVED') AND closed_at IS NOT NULL)
    );

CREATE INDEX idx_queues_business_status ON queues (business_id, status);
CREATE INDEX idx_queue_sessions_queue_status ON queue_sessions (queue_id, status);
