package com.syntagi.queue.repository;

import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.enums.QueueTokenStatus;
import com.syntagi.business.entity.Business;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueTokenRepository extends JpaRepository<QueueToken, UUID> {

    @EntityGraph(attributePaths = {"businessService", "customer"})
    @Query("""
            select qt from QueueToken qt
            where qt.business.id = :businessId
              and qt.queueSession.businessDate = :businessDate
            order by qt.queueOrder asc
            """)
    List<QueueToken> findDashboardTokens(
            @Param("businessId") UUID businessId,
            @Param("businessDate") java.time.LocalDate businessDate);

    @EntityGraph(attributePaths = "customer")
    @Query("""
            select qt from QueueToken qt
            where qt.queueSession.id = :queueSessionId
              and qt.status in (
                  com.syntagi.queue.enums.QueueTokenStatus.WAITING,
                  com.syntagi.queue.enums.QueueTokenStatus.CALLED,
                  com.syntagi.queue.enums.QueueTokenStatus.SKIPPED
              )
            """)
    List<QueueToken> findLiveByQueueSessionId(@Param("queueSessionId") UUID queueSessionId);

    @EntityGraph(attributePaths = "customer")
    Optional<QueueToken> findByQueueSessionIdAndTokenDisplay(
            UUID queueSessionId, String tokenDisplay);

    Optional<QueueToken> findByAppointmentId(UUID appointmentId);

    @Query("select qt.queueSession.id from QueueToken qt where qt.appointment.id = :appointmentId")
    Optional<UUID> findQueueSessionIdByAppointmentId(@Param("appointmentId") UUID appointmentId);

    @Query("""
            select qt.queueSession.id from QueueToken qt
            where qt.appointment.id = :appointmentId
              and qt.business.id = :businessId
            """)
    Optional<UUID> findQueueSessionIdByAppointmentIdAndBusinessId(
            @Param("appointmentId") UUID appointmentId,
            @Param("businessId") UUID businessId);

    @EntityGraph(attributePaths = {"queueSession", "business", "businessService", "customer"})
    Optional<QueueToken> findFirstByTokenDisplayAndCustomerMobileOrderByJoinedAtDesc(
            String tokenDisplay, String customerMobile);

    List<QueueToken> findByAppointmentIdIn(java.util.Collection<UUID> appointmentIds);

    @EntityGraph(attributePaths = {"business", "businessService", "customer", "appointment"})
    @Query("select qt from QueueToken qt where qt.id = :id")
    Optional<QueueToken> findNotificationContextById(@Param("id") UUID id);

    @Query("""
            select qt from QueueToken qt
            where qt.queueSession.id = :queueSessionId
              and qt.status = com.syntagi.queue.enums.QueueTokenStatus.WAITING
            order by qt.priority desc, qt.queueOrder asc
            """)
    List<QueueToken> findNextCandidates(
            @Param("queueSessionId") UUID queueSessionId, Pageable pageable);

    long countByQueueSessionIdAndStatus(UUID queueSessionId, QueueTokenStatus status);

    @EntityGraph(attributePaths = {"customer", "business"})
    @Query("select qt from QueueToken qt where qt.queueSession.id = :queueSessionId and qt.status = com.syntagi.queue.enums.QueueTokenStatus.WAITING")
    List<QueueToken> findWaitingForOrdering(@Param("queueSessionId") UUID queueSessionId);

    @Query("select qs.business from QueueSession qs where qs.id = :queueSessionId")
    Optional<Business> findBusinessByQueueSessionId(@Param("queueSessionId") UUID queueSessionId);
}
