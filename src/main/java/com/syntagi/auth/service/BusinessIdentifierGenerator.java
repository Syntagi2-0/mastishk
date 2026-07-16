package com.syntagi.auth.service;

import com.syntagi.business.repository.BusinessRepository;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BusinessIdentifierGenerator {

    private static final int MAX_SLUG_LENGTH = 120;
    private static final int BASE_LENGTH_WITH_SUFFIX = 110;

    private final BusinessRepository businessRepository;

    public BusinessIdentifierGenerator(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    public String uniqueSlug(String businessName) {
        String base = slugify(businessName);
        if (!businessRepository.existsBySlugIgnoreCase(base)) {
            return base;
        }
        do {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String candidate = truncate(base, BASE_LENGTH_WITH_SUFFIX) + "-" + suffix;
            if (!businessRepository.existsBySlugIgnoreCase(candidate)) {
                return candidate;
            }
        } while (true);
    }

    public String uniquePublicQueueCode() {
        do {
            String candidate = "Q-" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 12)
                    .toUpperCase(Locale.ROOT);
            if (!businessRepository.existsByPublicQueueCode(candidate)) {
                return candidate;
            }
        } while (true);
    }

    private static String slugify(String value) {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (normalized.isBlank()) {
            normalized = "business";
        }
        return truncate(normalized, MAX_SLUG_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
