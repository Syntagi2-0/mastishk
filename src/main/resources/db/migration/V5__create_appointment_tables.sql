CREATE TABLE appointment_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    business_service_id UUID NOT NULL,
    slot_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    capacity INTEGER NOT NULL DEFAULT 1,
    booked_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_appointment_slots_business FOREIGN KEY (business_id)
        REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_appointment_slots_service FOREIGN KEY (business_service_id)
        REFERENCES business_services (id) ON DELETE RESTRICT,
    CONSTRAINT uq_appointment_slots_service_date_time
        UNIQUE (business_service_id, slot_date, start_time),
    CONSTRAINT ck_appointment_slots_status
        CHECK (status IN ('AVAILABLE', 'BLOCKED', 'CLOSED')),
    CONSTRAINT ck_appointment_slots_capacity CHECK (capacity > 0),
    CONSTRAINT ck_appointment_slots_booked_count CHECK (booked_count >= 0),
    CONSTRAINT ck_appointment_slots_booked_within_capacity CHECK (booked_count <= capacity),
    CONSTRAINT ck_appointment_slots_times CHECK (end_time > start_time)
);

CREATE TABLE appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    business_service_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    appointment_slot_id UUID,
    booking_reference VARCHAR(40) NOT NULL,
    appointment_date DATE NOT NULL,
    scheduled_start_time TIME NOT NULL,
    scheduled_end_time TIME,
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    customer_notes VARCHAR(500),
    cancellation_reason VARCHAR(500),
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_appointments_business FOREIGN KEY (business_id)
        REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_service FOREIGN KEY (business_service_id)
        REFERENCES business_services (id) ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_slot FOREIGN KEY (appointment_slot_id)
        REFERENCES appointment_slots (id) ON DELETE SET NULL,
    CONSTRAINT uq_appointments_booking_reference UNIQUE (booking_reference),
    CONSTRAINT ck_appointments_status
        CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    CONSTRAINT ck_appointments_scheduled_times CHECK (
        scheduled_end_time IS NULL OR scheduled_end_time > scheduled_start_time
    )
);
