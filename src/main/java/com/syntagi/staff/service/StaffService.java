package com.syntagi.staff.service;

import com.syntagi.auth.entity.User;
import com.syntagi.auth.enums.BusinessRole;
import com.syntagi.auth.enums.BusinessUserStatus;
import com.syntagi.auth.repository.UserRepository;
import com.syntagi.auth.service.AuthenticatedBusinessContext;
import com.syntagi.auth.service.AuthenticatedBusinessContextService;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.staff.dto.request.CreateStaffRequest;
import com.syntagi.staff.dto.request.UpdateStaffStatusRequest;
import com.syntagi.staff.dto.response.StaffMemberResponse;
import com.syntagi.staff.dto.response.StaffMembershipResponse;
import com.syntagi.staff.entity.BusinessUser;
import com.syntagi.staff.mapper.StaffMapper;
import com.syntagi.staff.repository.BusinessUserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffService {

    private final AuthenticatedBusinessContextService contextService;
    private final UserRepository userRepository;
    private final BusinessUserRepository businessUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffMapper staffMapper;

    public StaffService(
            AuthenticatedBusinessContextService contextService,
            UserRepository userRepository,
            BusinessUserRepository businessUserRepository,
            PasswordEncoder passwordEncoder,
            StaffMapper staffMapper) {
        this.contextService = contextService;
        this.userRepository = userRepository;
        this.businessUserRepository = businessUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.staffMapper = staffMapper;
    }

    @Transactional
    public StaffMemberResponse createStaff(CreateStaffRequest request) {
        AuthenticatedBusinessContext context = contextService.requireOwner();
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(
                        request.fullName(),
                        email,
                        request.mobile(),
                        passwordEncoder.encode(request.temporaryPassword()))));

        if (businessUserRepository.existsByBusinessIdAndUserId(
                context.business().getId(), user.getId())) {
            throw new ApplicationException(ErrorCode.DUPLICATE_STAFF_MEMBERSHIP);
        }

        BusinessUser membership = businessUserRepository.saveAndFlush(
                new BusinessUser(context.business(), user, BusinessRole.STAFF));
        return staffMapper.toMemberResponse(membership);
    }

    @Transactional(readOnly = true)
    public List<StaffMemberResponse> listStaff(BusinessUserStatus status) {
        AuthenticatedBusinessContext context = contextService.requireOwner();
        List<BusinessUser> memberships = status == null
                ? businessUserRepository.findByBusinessIdAndRole(
                        context.business().getId(), BusinessRole.STAFF)
                : businessUserRepository.findByBusinessIdAndRoleAndStatus(
                        context.business().getId(), BusinessRole.STAFF, status);
        return memberships.stream().map(staffMapper::toMemberResponse).toList();
    }

    @Transactional
    public StaffMemberResponse updateStatus(UUID businessUserId, UpdateStaffStatusRequest request) {
        AuthenticatedBusinessContext context = contextService.requireOwner();
        BusinessUser membership = businessUserRepository
                .findByIdAndBusinessId(businessUserId, context.business().getId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.STAFF_NOT_FOUND));

        if (membership.getId().equals(context.membership().getId())
                && membership.getRole() == BusinessRole.OWNER
                && request.status() == BusinessUserStatus.INACTIVE) {
            throw new ApplicationException(ErrorCode.OWNER_SELF_DEACTIVATION_NOT_ALLOWED);
        }
        if (membership.getRole() != BusinessRole.STAFF) {
            throw new ApplicationException(ErrorCode.STAFF_NOT_FOUND);
        }

        if (request.status() == BusinessUserStatus.ACTIVE) {
            membership.activate();
        } else {
            membership.deactivate();
        }
        return staffMapper.toMemberResponse(membership);
    }

    @Transactional(readOnly = true)
    public StaffMembershipResponse currentMembership() {
        return staffMapper.toMembershipResponse(contextService.current().membership());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
