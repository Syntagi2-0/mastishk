package com.syntagi.auth.service;

import com.syntagi.auth.dto.request.LoginRequest;
import com.syntagi.auth.dto.request.RegisterOwnerRequest;
import com.syntagi.auth.dto.response.AuthResponse;
import com.syntagi.auth.dto.response.CurrentUserResponse;
import com.syntagi.auth.entity.User;
import com.syntagi.auth.enums.BusinessRole;
import com.syntagi.auth.enums.BusinessUserStatus;
import com.syntagi.auth.enums.UserStatus;
import com.syntagi.auth.exception.AuthException;
import com.syntagi.auth.mapper.AuthMapper;
import com.syntagi.auth.repository.UserRepository;
import com.syntagi.business.entity.Business;
import com.syntagi.business.enums.BusinessStatus;
import com.syntagi.business.repository.BusinessRepository;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.common.security.JwtTokenProvider;
import com.syntagi.common.security.SyntagiPrincipal;
import com.syntagi.staff.entity.BusinessUser;
import com.syntagi.staff.repository.BusinessUserRepository;
import java.util.Locale;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final BusinessUserRepository businessUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthMapper authMapper;
    private final BusinessIdentifierGenerator identifierGenerator;

    public AuthService(
            UserRepository userRepository,
            BusinessRepository businessRepository,
            BusinessUserRepository businessUserRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            AuthMapper authMapper,
            BusinessIdentifierGenerator identifierGenerator) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.businessUserRepository = businessUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authMapper = authMapper;
        this.identifierGenerator = identifierGenerator;
    }

    @Transactional
    public AuthResponse registerOwner(RegisterOwnerRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AuthException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = userRepository.save(new User(
                request.fullName(),
                email,
                request.mobile(),
                passwordEncoder.encode(request.password())));

        Business business = new Business(
                request.businessName(),
                identifierGenerator.uniqueSlug(request.businessName()),
                request.businessType(),
                identifierGenerator.uniquePublicQueueCode());
        business.updateContactDetails(email, request.mobile());
        business.updateAddress(null, null, null, null, request.country(), validTimezone(request.timezone()));
        business = businessRepository.save(business);

        BusinessUser membership = businessUserRepository.saveAndFlush(
                new BusinessUser(business, user, BusinessRole.OWNER));
        SyntagiPrincipal principal = toPrincipal(membership);
        return toAuthResponse(principal, user, business);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));
        rejectUnavailableUser(user);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (BadCredentialsException exception) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS);
        } catch (AuthenticationException exception) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!(authentication.getPrincipal() instanceof SyntagiPrincipal principal)) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS);
        }

        BusinessUser membership = activeMembership(principal);
        user.updateLastLogin();
        return toAuthResponse(principal, user, membership.getBusiness());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(SyntagiPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));
        rejectUnavailableUser(user);
        BusinessUser membership = activeMembership(principal);
        return new CurrentUserResponse(
                authMapper.toUserResponse(user),
                authMapper.toBusinessResponse(membership.getBusiness()),
                membership.getRole());
    }

    private BusinessUser activeMembership(SyntagiPrincipal principal) {
        BusinessUser membership = businessUserRepository
                .findByBusinessIdAndUserId(principal.businessId(), principal.userId())
                .orElseThrow(() -> new AuthException(ErrorCode.BUSINESS_ACCESS_NOT_FOUND));
        if (membership.getStatus() != BusinessUserStatus.ACTIVE
                || membership.getBusiness().getStatus() != BusinessStatus.ACTIVE) {
            throw new AuthException(ErrorCode.BUSINESS_ACCESS_NOT_FOUND);
        }
        return membership;
    }

    private void rejectUnavailableUser(User user) {
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AuthException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException(ErrorCode.ACCOUNT_INACTIVE);
        }
    }

    private AuthResponse toAuthResponse(SyntagiPrincipal principal, User user, Business business) {
        return new AuthResponse(
                jwtTokenProvider.generateAccessToken(principal),
                TOKEN_TYPE,
                jwtTokenProvider.getAccessTokenExpiresInSeconds(),
                authMapper.toUserResponse(user),
                authMapper.toBusinessResponse(business),
                principal.role());
    }

    private static SyntagiPrincipal toPrincipal(BusinessUser membership) {
        User user = membership.getUser();
        Business business = membership.getBusiness();
        return new SyntagiPrincipal(
                user.getId(),
                business.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                membership.getRole(),
                user.getStatus(),
                business.getStatus());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String validTimezone(String timezone) {
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException exception) {
            throw new com.syntagi.common.exception.ApplicationException(ErrorCode.INVALID_TIMEZONE);
        }
    }
}
