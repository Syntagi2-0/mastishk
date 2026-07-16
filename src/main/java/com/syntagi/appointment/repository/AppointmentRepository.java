package com.syntagi.appointment.repository;

import com.syntagi.appointment.entity.Appointment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findByBookingReference(String bookingReference);

    @Query("""
            select a from Appointment a
            where a.bookingReference = :bookingReference
              and a.customer.mobile = :customerMobile
            """)
    Optional<Appointment> findByBookingReferenceAndCustomerMobile(
            @Param("bookingReference") String bookingReference,
            @Param("customerMobile") String customerMobile);

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

    @EntityGraph(attributePaths = {"customer", "appointmentSlot"})
    @Query("""
            select a from Appointment a
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
