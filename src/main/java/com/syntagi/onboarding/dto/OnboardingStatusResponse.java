package com.syntagi.onboarding.dto;

public record OnboardingStatusResponse(
        boolean businessCreated,
        boolean profileCompleted,
        boolean firstQueueCreated,
        boolean setupCompleted) {
}
