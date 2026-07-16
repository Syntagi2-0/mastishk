package com.syntagi.business.repository;

import com.syntagi.business.entity.Business;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository extends JpaRepository<Business, UUID> {

    Optional<Business> findBySlugIgnoreCase(String slug);

    Optional<Business> findByPublicQueueCode(String publicQueueCode);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsByPublicQueueCode(String publicQueueCode);
}
