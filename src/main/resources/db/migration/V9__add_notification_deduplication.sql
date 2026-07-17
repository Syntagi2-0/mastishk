ALTER TABLE notifications
    ADD COLUMN deduplication_key VARCHAR(150);

CREATE UNIQUE INDEX uq_notifications_deduplication_key
    ON notifications (deduplication_key)
    WHERE deduplication_key IS NOT NULL;
