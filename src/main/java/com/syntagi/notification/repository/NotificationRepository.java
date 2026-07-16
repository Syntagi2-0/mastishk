package com.syntagi.notification.repository;

import com.syntagi.notification.entity.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @EntityGraph(attributePaths = "business")
    @Query("""
            select n from Notification n
            where n.status = com.syntagi.notification.enums.NotificationStatus.PENDING
            order by n.createdAt asc
            """)
    List<Notification> findPendingOrderByCreatedAtAsc();

    List<Notification> findByQueueTokenIdOrderByCreatedAtAsc(UUID queueTokenId);

    List<Notification> findByAppointmentIdOrderByCreatedAtAsc(UUID appointmentId);
}
