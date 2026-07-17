package com.syntagi.notification;

import static org.assertj.core.api.Assertions.*;

import com.syntagi.common.exception.ApplicationException;
import com.syntagi.notification.util.WhatsAppDeepLinkFormatter;
import org.junit.jupiter.api.Test;

class WhatsAppDeepLinkFormatterTest {

    private final WhatsAppDeepLinkFormatter formatter = new WhatsAppDeepLinkFormatter();

    @Test
    void formatsIndianAndValidInternationalNumbersAndEncodesMessage() {
        assertThat(formatter.create("98765 43210", "Token A001 is ready"))
                .isEqualTo("https://wa.me/919876543210?text=Token%20A001%20is%20ready");
        assertThat(formatter.normalizeMobile("+14155552671")).isEqualTo("14155552671");
        assertThat(formatter.normalizeMobile("919876543210")).isEqualTo("919876543210");
    }

    @Test
    void rejectsInvalidMobileNumbers() {
        assertThatThrownBy(() -> formatter.normalizeMobile("12345"))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> formatter.normalizeMobile("abcdefghij"))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> formatter.normalizeMobile("0123456789"))
                .isInstanceOf(ApplicationException.class);
    }
}
