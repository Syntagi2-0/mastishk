package com.syntagi.servicecatalog.repository;

import com.syntagi.servicecatalog.entity.BusinessService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BusinessServiceRepository extends JpaRepository<BusinessService, UUID>, JpaSpecificationExecutor<BusinessService> {

    long countByBusinessIdAndActiveTrue(UUID businessId);

    @EntityGraph(attributePaths = "business")
    List<BusinessService> findByBusinessIdAndActiveTrueOrderByDisplayOrderAscNameAsc(
            UUID businessId);

    @EntityGraph(attributePaths = "business")
    List<BusinessService> findByBusinessIdAndActiveOrderByDisplayOrderAscNameAsc(
            UUID businessId, boolean active);

    @EntityGraph(attributePaths = "business")
    Optional<BusinessService> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bs from BusinessService bs where bs.id = :id and bs.business.id = :businessId")
    Optional<BusinessService> findByIdAndBusinessIdForUpdate(
            @Param("id") UUID id, @Param("businessId") UUID businessId);

    @EntityGraph(attributePaths = "business")
    Optional<BusinessService> findWithBusinessById(UUID id);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

    boolean existsByBusinessIdAndServiceCodeIgnoreCase(UUID businessId, String serviceCode);

    boolean existsByBusinessIdAndServiceCodeIgnoreCaseAndIdNot(
            UUID businessId, String serviceCode, UUID id);

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(
            UUID businessId, String name, UUID id);
}
