package com.syntagi.appointment.controller;

import com.syntagi.appointment.dto.request.BookAppointmentRequest;
import com.syntagi.appointment.dto.request.CancelAppointmentRequest;
import com.syntagi.appointment.dto.response.PublicAppointmentResponse;
import com.syntagi.appointment.dto.response.PublicSlotResponse;
import com.syntagi.appointment.service.PublicAppointmentService;
import com.syntagi.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

@RestController
@SecurityRequirements
@RequestMapping("/api/public")
public class PublicAppointmentController {

    private final PublicAppointmentService appointmentService;

    public PublicAppointmentController(PublicAppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/businesses/{publicQueueCode}/services/{serviceId}/slots")
    public ApiResponse<List<PublicSlotResponse>> slots(
            @PathVariable String publicQueueCode,
            @PathVariable UUID serviceId,
            @RequestParam LocalDate date) {
        return ApiResponse.success(appointmentService.availableSlots(publicQueueCode, serviceId, date));
    }

    @PostMapping("/businesses/{publicQueueCode}/appointments")
    public ResponseEntity<ApiResponse<PublicAppointmentResponse>> book(
            @PathVariable String publicQueueCode,
            @Valid @RequestBody BookAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(appointmentService.book(publicQueueCode, request), "Appointment confirmed"));
    }

    @GetMapping("/appointments/{bookingReference}")
    public ApiResponse<PublicAppointmentResponse> lookup(
            @PathVariable String bookingReference, @RequestParam String mobile) {
        return ApiResponse.success(appointmentService.lookup(bookingReference, mobile));
    }

    @PostMapping("/appointments/{bookingReference}/cancel")
    public ApiResponse<PublicAppointmentResponse> cancel(
            @PathVariable String bookingReference,
            @Valid @RequestBody CancelAppointmentRequest request) {
        return ApiResponse.success(appointmentService.cancel(bookingReference, request), "Appointment cancelled");
    }
}
