package com.syntagi.appointment.controller;

import com.syntagi.appointment.dto.request.GenerateSlotsRequest;
import com.syntagi.appointment.dto.request.SlotStatusRequest;
import com.syntagi.appointment.dto.response.AppointmentSlotResponse;
import com.syntagi.appointment.dto.response.SlotGenerationResponse;
import com.syntagi.appointment.enums.AppointmentSlotStatus;
import com.syntagi.appointment.service.AppointmentSlotService;
import com.syntagi.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appointment-slots")
public class AppointmentSlotController {

    private final AppointmentSlotService slotService;

    public AppointmentSlotController(AppointmentSlotService slotService) {
        this.slotService = slotService;
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<SlotGenerationResponse>> generate(
            @Valid @RequestBody GenerateSlotsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(slotService.generate(request), "Appointment slots generated"));
    }

    @GetMapping
    public ApiResponse<List<AppointmentSlotResponse>> list(
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) AppointmentSlotStatus status) {
        return ApiResponse.success(slotService.list(serviceId, date, status));
    }

    @PatchMapping("/{slotId}/status")
    public ApiResponse<AppointmentSlotResponse> status(
            @PathVariable UUID slotId, @Valid @RequestBody SlotStatusRequest request) {
        return ApiResponse.success(slotService.updateStatus(slotId, request.status()), "Slot status updated");
    }
}
