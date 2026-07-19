package com.syntagi.queue.repository;

import com.syntagi.queue.entity.QueueConfiguration;
import com.syntagi.queue.enums.QueueStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueConfigurationRepository
        extends JpaRepository<QueueConfiguration, UUID> {

    @EntityGraph(attributePaths = {"business", "businessService"})
    List<QueueConfiguration> findByBusinessIdAndStatusNotOrderByNameAsc(
            UUID businessId, QueueStatus status);

    @EntityGraph(attributePaths = {"business", "businessService"})
    Optional<QueueConfiguration> findByIdAndBusinessId(UUID id, UUID businessId);

    @EntityGraph(attributePaths = {"business", "businessService"})
    Optional<QueueConfiguration> findByBusinessServiceId(UUID businessServiceId);

    Optional<QueueConfiguration> findByBusinessServiceIdAndStatus(
            UUID businessServiceId, QueueStatus status);

    boolean existsByBusinessIdAndBusinessServiceId(UUID businessId, UUID businessServiceId);

    boolean existsByBusinessId(UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select q from QueueConfiguration q
            join fetch q.business
            join fetch q.businessService
            where q.id = :id and q.business.id = :businessId
            """)
    Optional<QueueConfiguration> findByIdAndBusinessIdForUpdate(
            @Param("id") UUID id, @Param("businessId") UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select q from QueueConfiguration q
            join fetch q.business
            join fetch q.businessService
            where q.businessService.id = :serviceId
            """)
    Optional<QueueConfiguration> findByBusinessServiceIdForUpdate(
            @Param("serviceId") UUID serviceId);
}
