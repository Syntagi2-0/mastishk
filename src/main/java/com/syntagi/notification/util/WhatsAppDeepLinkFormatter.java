package com.syntagi.notification.util;

import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppDeepLinkFormatter {

    public String create(String mobile, String message) {
        String normalized = normalizeMobile(mobile);
        if (message == null || message.isBlank()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                    "WhatsApp message is required");
        }
        return "https://wa.me/" + normalized + "?text="
                + URLEncoder.encode(message.trim(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    public String normalizeMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            throw invalidMobile();
        }
        String value = mobile.trim().replaceAll("[\\s()-]", "");
        if (value.startsWith("+")) {
            value = value.substring(1);
        }
        if (!value.chars().allMatch(Character::isDigit)) {
            throw invalidMobile();
        }
        if (value.length() == 10 && value.charAt(0) >= '6') {
            return "91" + value;
        }
        if (value.length() >= 11 && value.length() <= 15 && value.charAt(0) != '0') {
            return value;
        }
        throw invalidMobile();
    }

    private static ApplicationException invalidMobile() {
        return new ApplicationException(ErrorCode.VALIDATION_FAILED,
                "Mobile number is not valid for a WhatsApp deep link");
    }
}
