package com.syntagi.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.models.GroupedOpenApi;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI syntagiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Syntagi Lite API")
                        .version("1.0.0")
                        .description("MVP API for business onboarding, services, queues, appointments, notifications and dashboards. Protected endpoints require a JWT bearer token.")
                        .contact(new Contact().name("Syntagi Lite")))
                .addTagsItem(new Tag().name("Authentication").description("Owner registration, login and current identity"))
                .addTagsItem(new Tag().name("Business").description("Business profile and staff administration"))
                .addTagsItem(new Tag().name("Services").description("Service catalog, schedules and appointment slots"))
                .addTagsItem(new Tag().name("Queue").description("Walk-ins and queue lifecycle operations"))
                .addTagsItem(new Tag().name("Appointments").description("Appointment booking and management"))
                .addTagsItem(new Tag().name("Notifications").description("Customer and business notifications"))
                .addTagsItem(new Tag().name("Dashboard").description("Today's operational summaries"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components().addSecuritySchemes(
                        BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .displayName("Public customer APIs")
                .pathsToMatch("/api/public/**")
                .build();
    }

    @Bean
    GroupedOpenApi businessApi() {
        return GroupedOpenApi.builder()
                .group("business")
                .displayName("Authenticated business APIs")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/public/**", "/api/auth/**")
                .build();
    }

    @Bean
    GroupedOpenApi authenticationApi() {
        return GroupedOpenApi.builder()
                .group("authentication")
                .displayName("Authentication APIs")
                .pathsToMatch("/api/auth/**")
                .build();
    }
}
