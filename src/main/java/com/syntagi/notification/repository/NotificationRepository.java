package com.syntagi.notification.repository;

import com.syntagi.notification.entity.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import com.syntagi.notification.enums.NotificationStatus;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    @Override
    @EntityGraph(attributePaths = {"business", "customer", "appointment", "queueToken"})
    Page<Notification> findAll(Specification<Notification> specification, Pageable pageable);

    Optional<Notification> findByDeduplicationKey(String deduplicationKey);

    boolean existsByDeduplicationKey(String deduplicationKey);

    long countByBusinessIdAndStatus(UUID businessId, NotificationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :id")
    Optional<Notification> findByIdForUpdate(@Param("id") UUID id);

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
