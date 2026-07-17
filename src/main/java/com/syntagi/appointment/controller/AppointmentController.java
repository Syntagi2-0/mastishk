package com.syntagi.appointment.controller;

import com.syntagi.appointment.dto.request.BusinessCancelAppointmentRequest;
import com.syntagi.appointment.dto.response.AppointmentResponse;
import com.syntagi.appointment.enums.AppointmentStatus;
import com.syntagi.appointment.service.BusinessAppointmentService;
import com.syntagi.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final BusinessAppointmentService appointmentService;

    public AppointmentController(BusinessAppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/today")
    public ApiResponse<List<AppointmentResponse>> today(
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) AppointmentStatus status) {
        return ApiResponse.success(appointmentService.today(serviceId, status));
    }

    @GetMapping
    public ApiResponse<Page<AppointmentResponse>> list(
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) String bookingReference,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ApiResponse.success(appointmentService.list(
                serviceId, dateFrom, dateTo, status, bookingReference, search, pageable));
    }

    @GetMapping("/{appointmentId}")
    public ApiResponse<AppointmentResponse> get(@PathVariable UUID appointmentId) {
        return ApiResponse.success(appointmentService.get(appointmentId));
    }

    @PostMapping("/{appointmentId}/cancel")
    public ApiResponse<AppointmentResponse> cancel(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody BusinessCancelAppointmentRequest request) {
        return ApiResponse.success(appointmentService.cancel(appointmentId, request.reason()), "Appointment cancelled");
    }
}
