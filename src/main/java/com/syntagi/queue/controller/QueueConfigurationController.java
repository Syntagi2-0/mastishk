package com.syntagi.queue.controller;

import com.syntagi.common.api.ApiResponse;
import com.syntagi.queue.dto.request.CreateQueueSessionForQueueRequest;
import com.syntagi.queue.dto.request.QueueUpsertRequest;
import com.syntagi.queue.dto.response.QueueConfigurationResponse;
import com.syntagi.queue.dto.response.QueueSessionResponse;
import com.syntagi.queue.service.QueueConfigurationService;
import com.syntagi.queue.service.QueueSessionLifecycleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queues")
public class QueueConfigurationController {

    private final QueueConfigurationService queueService;
    private final QueueSessionLifecycleService sessionService;

    public QueueConfigurationController(
            QueueConfigurationService queueService,
            QueueSessionLifecycleService sessionService) {
        this.queueService = queueService;
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QueueConfigurationResponse>> create(
            @Valid @RequestBody QueueUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(queueService.create(request), "Queue created"));
    }

    @GetMapping
    public ApiResponse<List<QueueConfigurationResponse>> list() {
        return ApiResponse.success(queueService.list());
    }

    @GetMapping("/{queueId}")
    public ApiResponse<QueueConfigurationResponse> get(@PathVariable UUID queueId) {
        return ApiResponse.success(queueService.get(queueId));
    }

    @PutMapping("/{queueId}")
    public ApiResponse<QueueConfigurationResponse> update(
            @PathVariable UUID queueId, @Valid @RequestBody QueueUpsertRequest request) {
        return ApiResponse.success(queueService.update(queueId, request), "Queue updated");
    }

    @PostMapping("/{queueId}/activate")
    public ApiResponse<QueueConfigurationResponse> activate(@PathVariable UUID queueId) {
        return ApiResponse.success(queueService.activate(queueId), "Queue activated");
    }

    @PostMapping("/{queueId}/pause")
    public ApiResponse<QueueConfigurationResponse> pause(@PathVariable UUID queueId) {
        return ApiResponse.success(queueService.pause(queueId), "Queue paused");
    }

    @PostMapping("/{queueId}/close")
    public ApiResponse<QueueConfigurationResponse> close(@PathVariable UUID queueId) {
        return ApiResponse.success(queueService.close(queueId), "Queue closed");
    }

    @PostMapping("/{queueId}/archive")
    public ApiResponse<QueueConfigurationResponse> archive(@PathVariable UUID queueId) {
        return ApiResponse.success(queueService.archive(queueId), "Queue archived");
    }

    @PostMapping("/{queueId}/sessions")
    public ResponseEntity<ApiResponse<QueueSessionResponse>> createSession(
            @PathVariable UUID queueId,
            @RequestBody(required = false) CreateQueueSessionForQueueRequest request) {
        CreateQueueSessionForQueueRequest body = request == null
                ? new CreateQueueSessionForQueueRequest(null, null) : request;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        sessionService.createToday(queueId, body), "Queue session created"));
    }
}
