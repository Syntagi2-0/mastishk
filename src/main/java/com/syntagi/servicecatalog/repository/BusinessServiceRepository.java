package com.syntagi.servicecatalog.repository;

import com.syntagi.servicecatalog.entity.BusinessService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessServiceRepository extends JpaRepository<BusinessService, UUID> {

    @EntityGraph(attributePaths = "business")
    List<BusinessService> findByBusinessIdAndActiveTrueOrderByDisplayOrderAscNameAsc(
            UUID businessId);

    @EntityGraph(attributePaths = "business")
    List<BusinessService> findByBusinessIdAndActiveOrderByDisplayOrderAscNameAsc(
            UUID businessId, boolean active);

    @EntityGraph(attributePaths = "business")
    Optional<BusinessService> findByIdAndBusinessId(UUID id, UUID businessId);

    @EntityGraph(attributePaths = "business")
    Optional<BusinessService> findWithBusinessById(UUID id);

    boolean existsByBusinessIdAndServiceCode(UUID businessId, String serviceCode);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

    boolean existsByBusinessIdAndServiceCodeIgnoreCase(UUID businessId, String serviceCode);

    boolean existsByBusinessIdAndServiceCodeIgnoreCaseAndIdNot(
            UUID businessId, String serviceCode, UUID id);

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(
            UUID businessId, String name, UUID id);
}
