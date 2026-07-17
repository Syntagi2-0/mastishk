package com.syntagi.appointment.repository;

import com.syntagi.appointment.entity.AppointmentSlot;
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

public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, UUID> {

    List<AppointmentSlot> findByBusinessIdAndBusinessServiceIdAndSlotDateOrderByStartTimeAsc(
            UUID businessId, UUID businessServiceId, LocalDate slotDate);

    List<AppointmentSlot> findByBusinessIdAndSlotDateOrderByStartTimeAsc(
            UUID businessId, LocalDate slotDate);

    List<AppointmentSlot> findByBusinessIdOrderBySlotDateAscStartTimeAsc(UUID businessId);

    boolean existsByBusinessServiceIdAndSlotDateAndStartTime(
            UUID businessServiceId, LocalDate slotDate, java.time.LocalTime startTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slot from AppointmentSlot slot where slot.id = :id and slot.business.id = :businessId")
    Optional<AppointmentSlot> findByIdAndBusinessIdForUpdate(
            @Param("id") UUID id, @Param("businessId") UUID businessId);

    @EntityGraph(attributePaths = "businessService")
    @Query("""
            select slot from AppointmentSlot slot
            where slot.businessService.id = :businessServiceId
              and slot.slotDate = :slotDate
              and slot.status = com.syntagi.appointment.enums.AppointmentSlotStatus.AVAILABLE
              and slot.bookedCount < slot.capacity
            order by slot.startTime asc
            """)
    List<AppointmentSlot> findAvailableByServiceAndDate(
            @Param("businessServiceId") UUID businessServiceId,
            @Param("slotDate") LocalDate slotDate);

    Optional<AppointmentSlot> findByIdAndBusinessServiceId(UUID id, UUID businessServiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select slot from AppointmentSlot slot
            where slot.id = :id and slot.businessService.id = :businessServiceId
            """)
    Optional<AppointmentSlot> findByIdAndBusinessServiceIdForUpdate(
            @Param("id") UUID id,
            @Param("businessServiceId") UUID businessServiceId);
}
