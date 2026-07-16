package com.syntagi.servicecatalog.repository;

import com.syntagi.servicecatalog.entity.ServiceSchedule;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceScheduleRepository extends JpaRepository<ServiceSchedule, UUID> {

    List<ServiceSchedule> findByBusinessServiceIdAndActiveTrue(UUID businessServiceId);

    @EntityGraph(attributePaths = {"businessService", "businessService.business"})
    List<ServiceSchedule> findByBusinessServiceIdOrderByDayOfWeekAscOperatingStartTimeAsc(
            UUID businessServiceId);

    @EntityGraph(attributePaths = {"businessService", "businessService.business"})
    Optional<ServiceSchedule> findByIdAndBusinessServiceId(UUID id, UUID businessServiceId);

    @Query("""
            select ss from ServiceSchedule ss
            where ss.businessService.id = :serviceId
              and ss.dayOfWeek = :dayOfWeek
              and ss.active = true
              and ss.operatingStartTime < :operatingEndTime
              and ss.operatingEndTime > :operatingStartTime
            """)
    List<ServiceSchedule> findActiveOverlaps(
            @Param("serviceId") UUID serviceId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("operatingStartTime") LocalTime operatingStartTime,
            @Param("operatingEndTime") LocalTime operatingEndTime);

    List<ServiceSchedule> findByDayOfWeekAndActiveTrue(DayOfWeek dayOfWeek);

    @EntityGraph(attributePaths = {"businessService", "businessService.business"})
    @Query("""
            select ss from ServiceSchedule ss
            where ss.dayOfWeek = :dayOfWeek
              and ss.active = true
              and ss.businessService.active = true
            order by ss.operatingStartTime asc
            """)
    List<ServiceSchedule> findActiveQueueOpeningCandidates(
            @Param("dayOfWeek") DayOfWeek dayOfWeek);

    @EntityGraph(attributePaths = {"businessService", "businessService.business"})
    @Query("""
            select ss from ServiceSchedule ss
            where ss.active = true
              and ss.businessService.active = true
              and ss.businessService.business.status = com.syntagi.business.enums.BusinessStatus.ACTIVE
            """)
    List<ServiceSchedule> findAllActiveForQueueScheduler();
}
