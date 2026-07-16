CREATE TABLE queue_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    business_service_id UUID NOT NULL,
    service_schedule_id UUID,
    business_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    token_counter INTEGER NOT NULL DEFAULT 0,
    appointment_token_counter INTEGER NOT NULL DEFAULT 0,
    walk_in_token_counter INTEGER NOT NULL DEFAULT 0,
    staff_token_counter INTEGER NOT NULL DEFAULT 0,
    current_token_id UUID,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_queue_sessions_business FOREIGN KEY (business_id)
        REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_queue_sessions_service FOREIGN KEY (business_service_id)
        REFERENCES business_services (id) ON DELETE RESTRICT,
    CONSTRAINT fk_queue_sessions_schedule FOREIGN KEY (service_schedule_id)
        REFERENCES service_schedules (id) ON DELETE RESTRICT,
    CONSTRAINT uq_queue_sessions_service_date UNIQUE (business_service_id, business_date),
    CONSTRAINT ck_queue_sessions_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_queue_sessions_token_counter CHECK (token_counter >= 0),
    CONSTRAINT ck_queue_sessions_appointment_counter CHECK (appointment_token_counter >= 0),
    CONSTRAINT ck_queue_sessions_walk_in_counter CHECK (walk_in_token_counter >= 0),
    CONSTRAINT ck_queue_sessions_staff_counter CHECK (staff_token_counter >= 0),
    CONSTRAINT ck_queue_sessions_closed_state CHECK (
        (status = 'OPEN' AND closed_at IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL)
    )
);

CREATE TABLE queue_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_session_id UUID NOT NULL,
    business_id UUID NOT NULL,
    business_service_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    appointment_id UUID,
    token_number INTEGER NOT NULL,
    token_display VARCHAR(30) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'WAITING',
    scheduled_time TIME,
    joined_at TIMESTAMPTZ NOT NULL,
    called_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    skipped_at TIMESTAMPTZ,
    queue_order BIGINT NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_queue_tokens_session FOREIGN KEY (queue_session_id)
        REFERENCES queue_sessions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_queue_tokens_business FOREIGN KEY (business_id)
        REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_queue_tokens_service FOREIGN KEY (business_service_id)
        REFERENCES business_services (id) ON DELETE RESTRICT,
    CONSTRAINT fk_queue_tokens_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_queue_tokens_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointments (id) ON DELETE RESTRICT,
    CONSTRAINT uq_queue_tokens_session_display UNIQUE (queue_session_id, token_display),
    CONSTRAINT uq_queue_tokens_session_order UNIQUE (queue_session_id, queue_order),
    CONSTRAINT ck_queue_tokens_source_type
        CHECK (source_type IN ('WALK_IN', 'APPOINTMENT', 'STAFF_CREATED')),
    CONSTRAINT ck_queue_tokens_status
        CHECK (status IN ('WAITING', 'CALLED', 'COMPLETED', 'SKIPPED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_queue_tokens_token_number CHECK (token_number > 0),
    CONSTRAINT ck_queue_tokens_priority CHECK (priority >= 0)
);

CREATE UNIQUE INDEX uq_queue_tokens_appointment
    ON queue_tokens (appointment_id)
    WHERE appointment_id IS NOT NULL;

ALTER TABLE queue_sessions
    ADD CONSTRAINT fk_queue_sessions_current_token
    FOREIGN KEY (current_token_id) REFERENCES queue_tokens (id) ON DELETE SET NULL;

COMMENT ON COLUMN queue_sessions.current_token_id IS
    'The service layer must ensure the current token belongs to this queue session.';
COMMENT ON COLUMN queue_tokens.token_display IS
    'Immutable customer-facing token label; formatting is performed by the application.';
COMMENT ON COLUMN queue_tokens.queue_order IS
    'Stable serving order independent of the customer-facing token display.';
