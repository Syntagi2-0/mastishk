package com.syntagi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntagi.auth.dto.request.RegisterOwnerRequest;
import com.syntagi.auth.entity.User;
import com.syntagi.auth.enums.BusinessRole;
import com.syntagi.auth.repository.UserRepository;
import com.syntagi.auth.service.AuthService;
import com.syntagi.business.entity.Business;
import com.syntagi.business.repository.BusinessRepository;
import com.syntagi.staff.entity.BusinessUser;
import com.syntagi.staff.repository.BusinessUserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthenticationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final BusinessUserRepository businessUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    @Autowired
    AuthenticationIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            BusinessRepository businessRepository,
            BusinessUserRepository businessUserRepository,
            PasswordEncoder passwordEncoder,
            AuthService authService) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.businessUserRepository = businessUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @Test
    void successfulOwnerRegistration() throws Exception {
        String email = uniqueEmail("register");

        mockMvc.perform(post("/api/auth/register-owner")
                        .contentType("application/json")
                        .content(registrationJson(email, "My Dental Clinic")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.business.name").value("My Dental Clinic"))
                .andExpect(jsonPath("$.data.business.slug").value("my-dental-clinic"))
                .andExpect(jsonPath("$.data.business.publicQueueCode").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("OWNER"));

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getPasswordHash()).doesNotContain("StrongPass123");
        assertThat(passwordEncoder.matches("StrongPass123", user.getPasswordHash())).isTrue();
        BusinessUser membership = businessUserRepository
                .findActiveByUserId(user.getId(), org.springframework.data.domain.PageRequest.of(0, 1))
                .getFirst();
        assertThat(membership.getRole()).isEqualTo(BusinessRole.OWNER);
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail("duplicate");
        register(email);

        mockMvc.perform(post("/api/auth/register-owner")
                        .contentType("application/json")
                        .content(registrationJson(email.toUpperCase(), "Another Business")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void successfulLoginUpdatesLastLogin() throws Exception {
        String email = uniqueEmail("login");
        register(email);
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getLastLoginAt()).isNull();

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginJson(email, "StrongPass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.business.name").value("Test Business"))
                .andExpect(jsonPath("$.data.role").value("OWNER"));

        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getLastLoginAt())
                .isNotNull();
    }

    @Test
    void incorrectPasswordIsRejected() throws Exception {
        String email = uniqueEmail("wrong-password");
        register(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginJson(email, "WrongPassword123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void inactiveUserIsRejected() throws Exception {
        String email = uniqueEmail("inactive");
        User user = createAccount(email);
        user.deactivate();
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginJson(email, "StrongPass123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void lockedUserIsRejected() throws Exception {
        String email = uniqueEmail("locked");
        User user = createAccount(email);
        user.lock();
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginJson(email, "StrongPass123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void protectedEndpointWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void authenticatedMeReturnsCurrentContext() throws Exception {
        String email = uniqueEmail("me");
        String token = accessToken(register(email));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.business.name").value("Test Business"))
                .andExpect(jsonPath("$.data.role").value("OWNER"));
    }

    @Test
    void registrationRollsBackWhenBusinessCreationFails() {
        String email = uniqueEmail("rollback");
        RegisterOwnerRequest request = new RegisterOwnerRequest(
                "Rollback Owner",
                email,
                "+919876543210",
                "StrongPass123",
                "Rollback Business",
                "X".repeat(51));

        assertThatThrownBy(() -> authService.registerOwner(request))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(userRepository.existsByEmailIgnoreCase(email)).isFalse();
    }

    private User createAccount(String email) {
        User user = userRepository.save(new User(
                "Test Owner", email, "+919876543210", passwordEncoder.encode("StrongPass123")));
        Business business = businessRepository.save(new Business(
                "Business " + UUID.randomUUID(),
                "business-" + UUID.randomUUID(),
                "CLINIC",
                "Q-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)));
        businessUserRepository.saveAndFlush(new BusinessUser(business, user, BusinessRole.OWNER));
        return user;
    }

    private MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register-owner")
                        .contentType("application/json")
                        .content(registrationJson(email, "Test Business")))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String registrationJson(String email, String businessName) throws Exception {
        return objectMapper.writeValueAsString(new RegisterOwnerRequest(
                "Test Owner",
                email,
                "+919876543210",
                "StrongPass123",
                businessName,
                "CLINIC"));
    }

    private String loginJson(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password));
    }

    private String accessToken(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("accessToken").asText();
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
