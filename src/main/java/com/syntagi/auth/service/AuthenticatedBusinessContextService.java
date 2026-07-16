package com.syntagi.auth.service;

import com.syntagi.auth.enums.BusinessRole;
import com.syntagi.auth.enums.BusinessUserStatus;
import com.syntagi.auth.enums.UserStatus;
import com.syntagi.business.enums.BusinessStatus;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.common.security.SyntagiPrincipal;
import com.syntagi.staff.entity.BusinessUser;
import com.syntagi.staff.repository.BusinessUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticatedBusinessContextService {

    private final BusinessUserRepository businessUserRepository;

    public AuthenticatedBusinessContextService(BusinessUserRepository businessUserRepository) {
        this.businessUserRepository = businessUserRepository;
    }

    public AuthenticatedBusinessContext current() {
        SyntagiPrincipal principal = currentPrincipal();
        BusinessUser membership = businessUserRepository
                .findByBusinessIdAndUserId(principal.businessId(), principal.userId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.BUSINESS_MEMBERSHIP_NOT_FOUND));

        if (membership.getStatus() != BusinessUserStatus.ACTIVE) {
            throw new ApplicationException(ErrorCode.INACTIVE_MEMBERSHIP);
        }
        if (membership.getUser().getStatus() != UserStatus.ACTIVE
                || membership.getBusiness().getStatus() != BusinessStatus.ACTIVE) {
            throw new ApplicationException(ErrorCode.BUSINESS_ACCESS_NOT_FOUND);
        }
        return new AuthenticatedBusinessContext(
                membership.getUser(), membership.getBusiness(), membership);
    }

    public AuthenticatedBusinessContext requireOwner() {
        AuthenticatedBusinessContext context = current();
        if (context.membership().getRole() != BusinessRole.OWNER) {
            throw new ApplicationException(ErrorCode.FORBIDDEN_ROLE);
        }
        return context;
    }

    private static SyntagiPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SyntagiPrincipal principal)) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }
}
