package com.syntagi.onboarding.service;

import com.syntagi.auth.service.AuthenticatedBusinessContextService;
import com.syntagi.business.entity.Business;
import com.syntagi.onboarding.dto.OnboardingStatusResponse;
import com.syntagi.queue.repository.QueueConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {

    private final AuthenticatedBusinessContextService contextService;
    private final QueueConfigurationRepository queueRepository;

    public OnboardingService(
            AuthenticatedBusinessContextService contextService,
            QueueConfigurationRepository queueRepository) {
        this.contextService = contextService;
        this.queueRepository = queueRepository;
    }

    @Transactional(readOnly = true)
    public OnboardingStatusResponse currentStatus() {
        Business business = contextService.current().business();
        boolean profileCompleted = hasText(business.getName())
                && hasText(business.getBusinessType())
                && hasText(business.getEmail())
                && hasText(business.getCountryCode())
                && hasText(business.getTimezone());
        boolean firstQueueCreated = queueRepository.existsByBusinessId(business.getId());
        return new OnboardingStatusResponse(
                true,
                profileCompleted,
                firstQueueCreated,
                profileCompleted && firstQueueCreated);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
