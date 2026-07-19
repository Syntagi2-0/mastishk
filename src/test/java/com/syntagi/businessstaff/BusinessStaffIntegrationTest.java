package com.syntagi.businessstaff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntagi.auth.dto.request.LoginRequest;
import com.syntagi.auth.dto.request.RegisterOwnerRequest;
import com.syntagi.auth.entity.User;
import com.syntagi.auth.enums.BusinessRole;
import com.syntagi.auth.enums.BusinessUserStatus;
import com.syntagi.auth.repository.UserRepository;
import com.syntagi.business.dto.request.UpdateBusinessRequest;
import com.syntagi.staff.dto.request.CreateStaffRequest;
import com.syntagi.staff.dto.request.UpdateStaffStatusRequest;
import com.syntagi.staff.entity.BusinessUser;
import com.syntagi.staff.repository.BusinessUserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class BusinessStaffIntegrationTest {

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
    private final BusinessUserRepository businessUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    BusinessStaffIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            BusinessUserRepository businessUserRepository,
            PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.businessUserRepository = businessUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Test
    void ownerReadsBusinessProfile() throws Exception {
        OwnerSession owner = registerOwner("read-business");

        mockMvc.perform(get("/api/business/me").header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(owner.businessId().toString()))
                .andExpect(jsonPath("$.data.name").value(owner.businessName()))
                .andExpect(jsonPath("$.data.slug").isNotEmpty())
                .andExpect(jsonPath("$.data.publicQueueCode").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void ownerUpdatesBusinessProfileWithoutChangingImmutableFields() throws Exception {
        OwnerSession owner = registerOwner("update-business");
        JsonNode before = businessProfile(owner.token());
        UpdateBusinessRequest request = updateBusinessRequest("Asia/Kolkata");

        mockMvc.perform(put("/api/business/me")
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Clinic"))
                .andExpect(jsonPath("$.data.businessType").value("DENTAL"))
                .andExpect(jsonPath("$.data.email").value("contact@example.com"))
                .andExpect(jsonPath("$.data.city").value("Bengaluru"))
                .andExpect(jsonPath("$.data.countryCode").value("IN"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.data.id").value(before.path("id").asText()))
                .andExpect(jsonPath("$.data.slug").value(before.path("slug").asText()))
                .andExpect(jsonPath("$.data.publicQueueCode")
                        .value(before.path("publicQueueCode").asText()))
                .andExpect(jsonPath("$.data.status").value(before.path("status").asText()));
    }

    @Test
    void invalidTimezoneIsRejected() throws Exception {
        OwnerSession owner = registerOwner("timezone");

        mockMvc.perform(put("/api/business/me")
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                updateBusinessRequest("Mars/Olympus_Mons"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));
    }

    @Test
    void staffCanReadBusinessProfileAndOwnMembership() throws Exception {
        OwnerSession owner = registerOwner("staff-read");
        String staffEmail = uniqueEmail("staff-read");
        createStaff(owner.token(), staffEmail);
        String staffToken = login(staffEmail, "Temporary123");

        mockMvc.perform(get("/api/business/me").header("Authorization", bearer(staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(owner.businessId().toString()));
        mockMvc.perform(get("/api/staff/me").header("Authorization", bearer(staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessId").value(owner.businessId().toString()))
                .andExpect(jsonPath("$.data.role").value("STAFF"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(get("/api/dashboard").header("Authorization", bearer(staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.business.name").isNotEmpty())
                .andExpect(jsonPath("$.data.totalActiveStaff").value(1));
    }

    @Test
    void staffCannotUpdateBusinessProfile() throws Exception {
        OwnerSession owner = registerOwner("staff-update-denied");
        String email = uniqueEmail("staff-update-denied");
        createStaff(owner.token(), email);
        String staffToken = login(email, "Temporary123");

        mockMvc.perform(put("/api/business/me")
                        .header("Authorization", bearer(staffToken))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                updateBusinessRequest("Asia/Kolkata"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));
    }

    @Test
    void ownerCreatesNewStaffUserWithHashedPassword() throws Exception {
        OwnerSession owner = registerOwner("create-staff");
        String email = uniqueEmail("new-staff");

        JsonNode staff = createStaff(owner.token(), email);

        assertThat(staff.path("role").asText()).isEqualTo("STAFF");
        assertThat(staff.path("status").asText()).isEqualTo("ACTIVE");
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(passwordEncoder.matches("Temporary123", user.getPasswordHash())).isTrue();
    }

    @Test
    void ownerConnectsExistingUserWithoutReplacingPassword() throws Exception {
        OwnerSession owner = registerOwner("connect-existing");
        String email = uniqueEmail("existing-staff");
        String existingHash = passwordEncoder.encode("ExistingPassword123");
        User existing = userRepository.saveAndFlush(
                new User("Existing User", email, "+919800000001", existingHash));

        JsonNode staff = createStaff(owner.token(), email);

        assertThat(staff.path("userId").asText()).isEqualTo(existing.getId().toString());
        assertThat(userRepository.findById(existing.getId()).orElseThrow().getPasswordHash())
                .isEqualTo(existingHash);
    }

    @Test
    void duplicateStaffMembershipIsRejected() throws Exception {
        OwnerSession owner = registerOwner("duplicate-staff");
        String email = uniqueEmail("duplicate-staff");
        createStaff(owner.token(), email);

        mockMvc.perform(post("/api/staff")
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(staffRequestJson(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_STAFF_MEMBERSHIP"));
    }

    @Test
    void ownerListsStaffWithOptionalStatusFilter() throws Exception {
        OwnerSession owner = registerOwner("list-staff");
        JsonNode active = createStaff(owner.token(), uniqueEmail("active-staff"));
        JsonNode inactive = createStaff(owner.token(), uniqueEmail("inactive-staff"));
        updateStaffStatus(owner.token(), inactive.path("businessUserId").asText(),
                BusinessUserStatus.INACTIVE);

        mockMvc.perform(get("/api/staff").header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/staff")
                        .param("status", "ACTIVE")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].businessUserId")
                        .value(active.path("businessUserId").asText()));
    }

    @Test
    void ownerDeactivatesStaff() throws Exception {
        OwnerSession owner = registerOwner("deactivate-staff");
        JsonNode staff = createStaff(owner.token(), uniqueEmail("deactivate-staff"));

        mockMvc.perform(put("/api/staff/{id}/status", staff.path("businessUserId").asText())
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new UpdateStaffStatusRequest(BusinessUserStatus.INACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void deactivatedStaffCannotAccessProtectedBusinessApis() throws Exception {
        OwnerSession owner = registerOwner("deactivated-access");
        String email = uniqueEmail("deactivated-access");
        JsonNode staff = createStaff(owner.token(), email);
        String staffToken = login(email, "Temporary123");
        updateStaffStatus(owner.token(), staff.path("businessUserId").asText(),
                BusinessUserStatus.INACTIVE);

        mockMvc.perform(get("/api/business/me").header("Authorization", bearer(staffToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void staffCannotManageStaff() throws Exception {
        OwnerSession owner = registerOwner("staff-manage-denied");
        String email = uniqueEmail("staff-manager");
        createStaff(owner.token(), email);
        String staffToken = login(email, "Temporary123");

        mockMvc.perform(post("/api/staff")
                        .header("Authorization", bearer(staffToken))
                        .contentType("application/json")
                        .content(staffRequestJson(uniqueEmail("forbidden-create"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));
        mockMvc.perform(get("/api/staff").header("Authorization", bearer(staffToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));
    }

    @Test
    void crossBusinessStaffAccessIsBlocked() throws Exception {
        OwnerSession firstOwner = registerOwner("first-business");
        OwnerSession secondOwner = registerOwner("second-business");
        JsonNode secondStaff = createStaff(secondOwner.token(), uniqueEmail("second-staff"));

        mockMvc.perform(put("/api/staff/{id}/status",
                                secondStaff.path("businessUserId").asText())
                        .header("Authorization", bearer(firstOwner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new UpdateStaffStatusRequest(BusinessUserStatus.INACTIVE))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAFF_NOT_FOUND"));
    }

    @Test
    void ownerCannotDeactivateOwnMembership() throws Exception {
        OwnerSession owner = registerOwner("owner-self-deactivate");
        BusinessUser ownerMembership = businessUserRepository
                .findByBusinessIdAndUserId(owner.businessId(), owner.userId())
                .orElseThrow();

        mockMvc.perform(put("/api/staff/{id}/status", ownerMembership.getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new UpdateStaffStatusRequest(BusinessUserStatus.INACTIVE))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("OWNER_SELF_DEACTIVATION_NOT_ALLOWED"));
    }

    private OwnerSession registerOwner(String key) throws Exception {
        String email = uniqueEmail(key + "-owner");
        String businessName = "Business " + key + " " + UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/auth/register-owner")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RegisterOwnerRequest(
                                "Owner " + key,
                                businessName,
                                email,
                                "OwnerPassword123",
                                "Asia/Kolkata"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = responseData(result);
        return new OwnerSession(
                data.path("accessToken").asText(),
                UUID.fromString(data.path("user").path("id").asText()),
                UUID.fromString(data.path("business").path("id").asText()),
                businessName);
    }

    private JsonNode createStaff(String ownerToken, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/staff")
                        .header("Authorization", bearer(ownerToken))
                        .contentType("application/json")
                        .content(staffRequestJson(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseData(result);
    }

    private void updateStaffStatus(
            String ownerToken, String businessUserId, BusinessUserStatus statusValue)
            throws Exception {
        mockMvc.perform(put("/api/staff/{id}/status", businessUserId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new UpdateStaffStatusRequest(statusValue))))
                .andExpect(status().isOk());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return responseData(result).path("accessToken").asText();
    }

    private JsonNode businessProfile(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/business/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return responseData(result);
    }

    private String staffRequestJson(String email) throws Exception {
        return objectMapper.writeValueAsString(new CreateStaffRequest(
                "Staff User", email, "+919800000002", "Temporary123"));
    }

    private static UpdateBusinessRequest updateBusinessRequest(String timezone) {
        return new UpdateBusinessRequest(
                "  Updated Clinic  ",
                " DENTAL ",
                " CONTACT@EXAMPLE.COM ",
                "+919900000003",
                "  10 Main Road  ",
                "  Bengaluru  ",
                "  Karnataka  ",
                " 560001 ",
                "in",
                timezone);
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private record OwnerSession(
            String token, UUID userId, UUID businessId, String businessName) {
    }
}
