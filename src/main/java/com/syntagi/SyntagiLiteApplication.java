package com.syntagi;

import com.syntagi.common.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
@EnableScheduling
@EnableConfigurationProperties(JwtProperties.class)
@SpringBootApplication
public class SyntagiLiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyntagiLiteApplication.class, args);
    }
}
