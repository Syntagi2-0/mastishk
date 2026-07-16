CREATE INDEX idx_business_users_business ON business_users (business_id);
CREATE INDEX idx_business_users_user ON business_users (user_id);
CREATE INDEX idx_business_users_business_role_status
    ON business_users (business_id, role, status);

CREATE INDEX idx_customers_business ON customers (business_id);
CREATE INDEX idx_business_services_business ON business_services (business_id);
CREATE INDEX idx_service_schedules_service ON service_schedules (business_service_id);

CREATE INDEX idx_appointment_slots_service_date_status
    ON appointment_slots (business_service_id, slot_date, status);
CREATE INDEX idx_appointment_slots_business_date
    ON appointment_slots (business_id, slot_date);

CREATE INDEX idx_appointments_business_date_status
    ON appointments (business_id, appointment_date, status);
CREATE INDEX idx_appointments_service_date_start
    ON appointments (business_service_id, appointment_date, scheduled_start_time);
CREATE INDEX idx_appointments_customer ON appointments (customer_id);
CREATE INDEX idx_appointments_slot ON appointments (appointment_slot_id);

CREATE INDEX idx_queue_sessions_business_date
    ON queue_sessions (business_id, business_date);
CREATE INDEX idx_queue_sessions_status_date
    ON queue_sessions (status, business_date);
CREATE INDEX idx_queue_sessions_schedule ON queue_sessions (service_schedule_id);

CREATE INDEX idx_queue_tokens_session_status_priority_order
    ON queue_tokens (queue_session_id, status, priority DESC, queue_order);
CREATE INDEX idx_queue_tokens_session_status_scheduled
    ON queue_tokens (queue_session_id, status, scheduled_time);
CREATE INDEX idx_queue_tokens_business_created
    ON queue_tokens (business_id, created_at);
CREATE INDEX idx_queue_tokens_customer ON queue_tokens (customer_id);
CREATE INDEX idx_queue_tokens_token_display ON queue_tokens (token_display);
CREATE INDEX idx_queue_tokens_status ON queue_tokens (status);

CREATE INDEX idx_notifications_business_created
    ON notifications (business_id, created_at);
CREATE INDEX idx_notifications_status_created
    ON notifications (status, created_at);
CREATE INDEX idx_notifications_customer ON notifications (customer_id);
CREATE INDEX idx_notifications_queue_token ON notifications (queue_token_id);
CREATE INDEX idx_notifications_appointment ON notifications (appointment_id);
