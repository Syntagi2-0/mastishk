package com.syntagi.queue.controller;

import com.syntagi.common.api.ApiResponse;
import com.syntagi.queue.dto.response.QueueCurrentResponse;
import com.syntagi.queue.dto.response.WaitingQueueTokenResponse;
import com.syntagi.queue.service.QueueManagementService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueManagementService queueManagementService;

    public QueueController(QueueManagementService queueManagementService) {
        this.queueManagementService = queueManagementService;
    }

    @GetMapping("/current")
    public ApiResponse<QueueCurrentResponse> current(
            @RequestParam(required = false) UUID serviceId) {
        return ApiResponse.success(queueManagementService.current(serviceId));
    }

    @GetMapping("/waiting")
    public ApiResponse<List<WaitingQueueTokenResponse>> waiting(
            @RequestParam(required = false) UUID serviceId) {
        return ApiResponse.success(queueManagementService.waiting(serviceId));
    }

    @PostMapping("/next")
    public ApiResponse<QueueCurrentResponse> next(
            @RequestParam(required = false) UUID serviceId) {
        return ApiResponse.success(queueManagementService.next(serviceId), "Queue advanced");
    }

    @PostMapping("/current/skip")
    public ApiResponse<QueueCurrentResponse> skip(
            @RequestParam(required = false) UUID serviceId) {
        return ApiResponse.success(queueManagementService.skip(serviceId), "Current token skipped");
    }

    @PostMapping("/current/recall")
    public ApiResponse<QueueCurrentResponse> recall(
            @RequestParam(required = false) UUID serviceId) {
        return ApiResponse.success(queueManagementService.recall(serviceId), "Current token recalled");
    }
}
