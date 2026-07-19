package com.syntagi.onboarding.controller;

import com.syntagi.common.api.ApiResponse;
import com.syntagi.onboarding.dto.OnboardingStatusResponse;
import com.syntagi.onboarding.service.OnboardingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping("/status")
    public ApiResponse<OnboardingStatusResponse> status() {
        return ApiResponse.success(onboardingService.currentStatus());
    }
}
