CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_customers_business_name_search
    ON customers USING gin (lower(full_name) gin_trgm_ops);
CREATE INDEX idx_customers_business_mobile_search
    ON customers USING gin (mobile gin_trgm_ops);

CREATE INDEX idx_business_services_name_search
    ON business_services USING gin (lower(name) gin_trgm_ops);
CREATE INDEX idx_business_services_code_search
    ON business_services USING gin (lower(service_code) gin_trgm_ops);

CREATE INDEX idx_appointments_booking_reference_search
    ON appointments USING gin (lower(booking_reference) gin_trgm_ops);

CREATE INDEX idx_notifications_business_status_type_channel_created
    ON notifications (business_id, status, notification_type, channel, created_at DESC);

CREATE INDEX idx_queue_tokens_business_session_status
    ON queue_tokens (business_id, queue_session_id, status);
