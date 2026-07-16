package com.syntagi.staff.repository;

import com.syntagi.auth.enums.BusinessUserStatus;
import com.syntagi.auth.enums.BusinessRole;
import com.syntagi.staff.entity.BusinessUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

public interface BusinessUserRepository extends JpaRepository<BusinessUser, UUID> {

    @EntityGraph(attributePaths = {"business", "user"})
    @Query("""
            select bu from BusinessUser bu
            where bu.business.id = :businessId and bu.user.id = :userId
            """)
    Optional<BusinessUser> findByBusinessIdAndUserId(
            @Param("businessId") UUID businessId, @Param("userId") UUID userId);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select bu from BusinessUser bu
            where bu.business.id = :businessId and bu.status = :status
            order by bu.createdAt asc
            """)
    List<BusinessUser> findByBusinessIdAndStatus(
            @Param("businessId") UUID businessId,
            @Param("status") BusinessUserStatus status);

    @Query("""
            select (count(bu) > 0) from BusinessUser bu
            where bu.business.id = :businessId and bu.user.id = :userId
            """)
    boolean existsByBusinessIdAndUserId(
            @Param("businessId") UUID businessId, @Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"business", "user"})
    @Query("""
            select bu from BusinessUser bu
            where bu.user.id = :userId
              and bu.status = com.syntagi.auth.enums.BusinessUserStatus.ACTIVE
            order by bu.createdAt asc
            """)
    List<BusinessUser> findActiveByUserId(
            @Param("userId") UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"business", "user"})
    @Query("""
            select bu from BusinessUser bu
            where bu.business.id = :businessId and bu.role = :role
            order by bu.createdAt asc
            """)
    List<BusinessUser> findByBusinessIdAndRole(
            @Param("businessId") UUID businessId,
            @Param("role") BusinessRole role);

    @EntityGraph(attributePaths = {"business", "user"})
    @Query("""
            select bu from BusinessUser bu
            where bu.business.id = :businessId
              and bu.role = :role
              and bu.status = :status
            order by bu.createdAt asc
            """)
    List<BusinessUser> findByBusinessIdAndRoleAndStatus(
            @Param("businessId") UUID businessId,
            @Param("role") BusinessRole role,
            @Param("status") BusinessUserStatus status);

    @EntityGraph(attributePaths = {"business", "user"})
    @Query("""
            select bu from BusinessUser bu
            where bu.id = :id and bu.business.id = :businessId
            """)
    Optional<BusinessUser> findByIdAndBusinessId(
            @Param("id") UUID id,
            @Param("businessId") UUID businessId);
}
