package com.syntagi.appointment.repository;

import com.syntagi.appointment.entity.Appointment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    @Override
    @EntityGraph(attributePaths = {"business", "businessService", "customer", "appointmentSlot"})
    Page<Appointment> findAll(Specification<Appointment> specification, Pageable pageable);

    Optional<Appointment> findByBookingReference(String bookingReference);

    @Query("select a.id from Appointment a where a.bookingReference = :bookingReference")
    Optional<UUID> findIdByBookingReference(@Param("bookingReference") String bookingReference);

    boolean existsByBookingReference(String bookingReference);

    @EntityGraph(attributePaths = {"business", "businessService", "customer", "appointmentSlot"})
    Optional<Appointment> findDetailedByBookingReference(String bookingReference);

    @EntityGraph(attributePaths = {"business", "businessService", "customer", "appointmentSlot"})
    Optional<Appointment> findDetailedByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.bookingReference = :bookingReference")
    Optional<Appointment> findByBookingReferenceForUpdate(
            @Param("bookingReference") String bookingReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id and a.business.id = :businessId")
    Optional<Appointment> findByIdAndBusinessIdForUpdate(
            @Param("id") UUID id, @Param("businessId") UUID businessId);

    long countByAppointmentSlotIdAndStatus(
            UUID appointmentSlotId, com.syntagi.appointment.enums.AppointmentStatus status);

    @EntityGraph(attributePaths = {"customer", "appointmentSlot"})
    @Query("""
            select a from Appointment a
            where a.businessService.id = :businessServiceId
              and a.appointmentDate = :appointmentDate
              and a.status = com.syntagi.appointment.enums.AppointmentStatus.CONFIRMED
            order by a.scheduledStartTime asc
            """)
    List<Appointment> findConfirmedByServiceAndDate(
            @Param("businessServiceId") UUID businessServiceId,
            @Param("appointmentDate") LocalDate appointmentDate);

    @EntityGraph(attributePaths = {"businessService", "customer"})
    List<Appointment> findByBusinessIdAndAppointmentDateOrderByScheduledStartTimeAsc(
            UUID businessId, LocalDate appointmentDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from Appointment a
            join fetch a.customer
            where a.businessService.id = :businessServiceId
              and a.appointmentDate = :appointmentDate
              and a.status = com.syntagi.appointment.enums.AppointmentStatus.CONFIRMED
              and not exists (
                  select qt.id from QueueToken qt where qt.appointment = a
              )
            order by a.scheduledStartTime asc, a.createdAt asc
            """)
    List<Appointment> findConfirmedWithoutQueueToken(
            @Param("businessServiceId") UUID businessServiceId,
            @Param("appointmentDate") LocalDate appointmentDate);
}
