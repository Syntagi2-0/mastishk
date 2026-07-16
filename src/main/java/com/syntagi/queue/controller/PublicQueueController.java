package com.syntagi.queue.controller;

import com.syntagi.common.api.ApiResponse;
import com.syntagi.queue.dto.request.WalkInRequest;
import com.syntagi.queue.dto.response.LiveQueueResponse;
import com.syntagi.queue.dto.response.WalkInTokenResponse;
import com.syntagi.queue.service.PublicQueueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicQueueController {

    private final PublicQueueService publicQueueService;

    public PublicQueueController(PublicQueueService publicQueueService) {
        this.publicQueueService = publicQueueService;
    }

    @PostMapping("/businesses/{publicQueueCode}/walk-in")
    public ResponseEntity<ApiResponse<WalkInTokenResponse>> joinWalkIn(
            @PathVariable String publicQueueCode,
            @Valid @RequestBody WalkInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        publicQueueService.joinWalkIn(publicQueueCode, request),
                        "Walk-in token created"));
    }

    @GetMapping("/queue/{tokenDisplay}")
    public ApiResponse<LiveQueueResponse> liveQueue(@PathVariable String tokenDisplay) {
        return ApiResponse.success(publicQueueService.getLiveQueue(tokenDisplay));
    }
}
