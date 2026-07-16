CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    mobile VARCHAR(20) NOT NULL,
    email VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_customers_business FOREIGN KEY (business_id)
        REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT uq_customers_business_mobile UNIQUE (business_id, mobile)
);

CREATE TABLE business_services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    service_code VARCHAR(50) NOT NULL,
    service_mode VARCHAR(30) NOT NULL,
    expected_duration_minutes INTEGER,
    appointment_slot_duration_minutes INTEGER,
    active BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_business_services_business FOREIGN KEY (business_id)
        REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT uq_business_services_business_code UNIQUE (business_id, service_code),
    CONSTRAINT ck_business_services_mode
        CHECK (service_mode IN ('WALK_IN', 'APPOINTMENT', 'BOTH')),
    CONSTRAINT ck_business_services_expected_duration
        CHECK (expected_duration_minutes IS NULL OR expected_duration_minutes > 0),
    CONSTRAINT ck_business_services_slot_duration
        CHECK (appointment_slot_duration_minutes IS NULL OR appointment_slot_duration_minutes > 0)
);

CREATE UNIQUE INDEX uq_business_services_business_name_lower
    ON business_services (business_id, lower(name));

CREATE TABLE service_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_service_id UUID NOT NULL,
    day_of_week SMALLINT NOT NULL,
    operating_start_time TIME NOT NULL,
    operating_end_time TIME NOT NULL,
    queue_open_before_minutes INTEGER NOT NULL DEFAULT 0,
    appointment_booking_enabled BOOLEAN NOT NULL DEFAULT true,
    walk_in_enabled BOOLEAN NOT NULL DEFAULT true,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_service_schedules_service FOREIGN KEY (business_service_id)
        REFERENCES business_services (id) ON DELETE CASCADE,
    CONSTRAINT uq_service_schedules_window
        UNIQUE (business_service_id, day_of_week, operating_start_time),
    CONSTRAINT ck_service_schedules_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_service_schedules_operating_times
        CHECK (operating_end_time > operating_start_time),
    CONSTRAINT ck_service_schedules_queue_open_before
        CHECK (queue_open_before_minutes BETWEEN 0 AND 1440)
);
