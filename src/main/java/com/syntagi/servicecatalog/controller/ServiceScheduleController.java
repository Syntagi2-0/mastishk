package com.syntagi.servicecatalog.controller;

import com.syntagi.common.api.ApiResponse;
import com.syntagi.servicecatalog.dto.request.ScheduleStatusRequest;
import com.syntagi.servicecatalog.dto.request.ScheduleUpsertRequest;
import com.syntagi.servicecatalog.dto.response.ScheduleResponse;
import com.syntagi.servicecatalog.service.ServiceScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services/{serviceId}/schedules")
public class ServiceScheduleController {

    private final ServiceScheduleService scheduleService;

    public ServiceScheduleController(ServiceScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleResponse>> create(
            @PathVariable UUID serviceId,
            @Valid @RequestBody ScheduleUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        scheduleService.create(serviceId, request), "Service schedule created"));
    }

    @GetMapping
    public ApiResponse<List<ScheduleResponse>> list(@PathVariable UUID serviceId) {
        return ApiResponse.success(scheduleService.list(serviceId));
    }

    @PutMapping("/{scheduleId}")
    public ApiResponse<ScheduleResponse> update(
            @PathVariable UUID serviceId,
            @PathVariable UUID scheduleId,
            @Valid @RequestBody ScheduleUpsertRequest request) {
        return ApiResponse.success(
                scheduleService.update(serviceId, scheduleId, request),
                "Service schedule updated");
    }

    @PatchMapping("/{scheduleId}/status")
    public ApiResponse<ScheduleResponse> updateStatus(
            @PathVariable UUID serviceId,
            @PathVariable UUID scheduleId,
            @Valid @RequestBody ScheduleStatusRequest request) {
        return ApiResponse.success(
                scheduleService.updateStatus(serviceId, scheduleId, request),
                "Service schedule status updated");
    }
}
