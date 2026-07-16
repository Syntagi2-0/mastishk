package com.syntagi.queue.repository;

import com.syntagi.queue.entity.QueueSession;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueSessionRepository extends JpaRepository<QueueSession, UUID> {

    @EntityGraph(attributePaths = {"business", "businessService", "serviceSchedule", "currentToken"})
    Optional<QueueSession> findByBusinessServiceIdAndBusinessDate(
            UUID businessServiceId, LocalDate businessDate);

    @EntityGraph(attributePaths = {"businessService", "currentToken"})
    List<QueueSession> findByBusinessIdAndBusinessDate(UUID businessId, LocalDate businessDate);

    @Query("""
            select qs.businessService.id from QueueSession qs
            where qs.business.id = :businessId
              and qs.businessDate = :businessDate
            """)
    List<UUID> findServiceIdsByBusinessIdAndBusinessDate(
            @Param("businessId") UUID businessId,
            @Param("businessDate") LocalDate businessDate);

    boolean existsByBusinessServiceIdAndBusinessDate(
            UUID businessServiceId, LocalDate businessDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select qs from QueueSession qs where qs.id = :id")
    Optional<QueueSession> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select qs from QueueSession qs
            where qs.business.id = :businessId
              and qs.businessService.id = :serviceId
              and qs.businessDate = :businessDate
            """)
    Optional<QueueSession> findTodayForUpdate(
            @Param("businessId") UUID businessId,
            @Param("serviceId") UUID serviceId,
            @Param("businessDate") LocalDate businessDate);

    @EntityGraph(attributePaths = {"businessService", "currentToken", "currentToken.customer"})
    Optional<QueueSession> findByBusinessIdAndBusinessServiceIdAndBusinessDate(
            UUID businessId, UUID businessServiceId, LocalDate businessDate);
}
