package com.syntagi.queue.controller;

import com.syntagi.common.api.ApiResponse;
import com.syntagi.queue.dto.request.CreateQueueSessionRequest;
import com.syntagi.queue.dto.response.QueueSessionResponse;
import com.syntagi.queue.service.QueueSessionLifecycleService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queue-sessions")
public class QueueSessionController {

    private final QueueSessionLifecycleService lifecycleService;

    public QueueSessionController(QueueSessionLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QueueSessionResponse>> create(
            @Valid @RequestBody CreateQueueSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        lifecycleService.createToday(request), "Queue session created"));
    }

    @PostMapping("/{queueSessionId}/close")
    public ApiResponse<QueueSessionResponse> close(@PathVariable UUID queueSessionId) {
        return ApiResponse.success(lifecycleService.close(queueSessionId), "Queue session closed");
    }

    @PostMapping("/{queueSessionId}/open")
    public ApiResponse<QueueSessionResponse> open(@PathVariable UUID queueSessionId) {
        return ApiResponse.success(lifecycleService.open(queueSessionId), "Queue session opened");
    }

    @PostMapping("/{queueSessionId}/pause")
    public ApiResponse<QueueSessionResponse> pause(@PathVariable UUID queueSessionId) {
        return ApiResponse.success(lifecycleService.pause(queueSessionId), "Queue session paused");
    }

    @PostMapping("/{queueSessionId}/resume")
    public ApiResponse<QueueSessionResponse> resume(@PathVariable UUID queueSessionId) {
        return ApiResponse.success(lifecycleService.resume(queueSessionId), "Queue session resumed");
    }
}
