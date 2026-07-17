package com.syntagi.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "syntagi.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        @NotNull Duration accessTokenTtl,
        @NotBlank String issuer) {
}
