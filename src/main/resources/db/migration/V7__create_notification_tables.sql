CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    customer_id UUID,
    appointment_id UUID,
    queue_token_id UUID,
    channel VARCHAR(30) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    recipient VARCHAR(255),
    title VARCHAR(200),
    message VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(1000),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_notifications_business FOREIGN KEY (business_id)
        REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notifications_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notifications_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notifications_queue_token FOREIGN KEY (queue_token_id)
        REFERENCES queue_tokens (id) ON DELETE RESTRICT,
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('BROWSER', 'WHATSAPP_LINK')),
    CONSTRAINT ck_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);
