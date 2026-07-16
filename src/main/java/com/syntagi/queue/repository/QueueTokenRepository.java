package com.syntagi.queue.repository;

import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.enums.QueueTokenStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueTokenRepository extends JpaRepository<QueueToken, UUID> {

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
    List<QueueToken> findByQueueSessionIdAndStatus(
            UUID queueSessionId, QueueTokenStatus status, Sort sort);

    @EntityGraph(attributePaths = "customer")
    Optional<QueueToken> findByQueueSessionIdAndTokenDisplay(
            UUID queueSessionId, String tokenDisplay);

    Optional<QueueToken> findByAppointmentId(UUID appointmentId);

    @Query("""
            select count(qt) from QueueToken qt
            where qt.queueSession.id = :queueSessionId
              and qt.status = com.syntagi.queue.enums.QueueTokenStatus.WAITING
            """)
    long countWaitingByQueueSessionId(@Param("queueSessionId") UUID queueSessionId);

    @Query("""
            select qt from QueueToken qt
            where qt.queueSession.id = :queueSessionId
              and qt.status = com.syntagi.queue.enums.QueueTokenStatus.SKIPPED
            order by qt.queueOrder asc
            """)
    List<QueueToken> findSkippedByQueueSessionId(@Param("queueSessionId") UUID queueSessionId);

    @Query("""
            select qt from QueueToken qt
            where qt.queueSession.id = :queueSessionId
              and qt.status = com.syntagi.queue.enums.QueueTokenStatus.WAITING
            order by qt.priority desc, qt.queueOrder asc
            """)
    List<QueueToken> findNextCandidates(
            @Param("queueSessionId") UUID queueSessionId, Pageable pageable);

    @EntityGraph(attributePaths = {"queueSession", "queueSession.currentToken"})
    Optional<QueueToken> findFirstByTokenDisplayOrderByJoinedAtDesc(String tokenDisplay);

    long countByQueueSessionIdAndStatus(UUID queueSessionId, QueueTokenStatus status);
}
