package com.syntagi.business.service;

import com.syntagi.auth.service.AuthenticatedBusinessContext;
import com.syntagi.auth.service.AuthenticatedBusinessContextService;
import com.syntagi.business.dto.request.UpdateBusinessRequest;
import com.syntagi.business.dto.response.BusinessProfileResponse;
import com.syntagi.business.entity.Business;
import com.syntagi.business.mapper.BusinessMapper;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessProfileService {

    private final AuthenticatedBusinessContextService contextService;
    private final BusinessMapper businessMapper;

    public BusinessProfileService(
            AuthenticatedBusinessContextService contextService,
            BusinessMapper businessMapper) {
        this.contextService = contextService;
        this.businessMapper = businessMapper;
    }

    @Transactional(readOnly = true)
    public BusinessProfileResponse getCurrentBusiness() {
        return businessMapper.toProfileResponse(contextService.current().business());
    }

    @Transactional
    public BusinessProfileResponse updateCurrentBusiness(UpdateBusinessRequest request) {
        AuthenticatedBusinessContext context = contextService.requireOwner();
        String timezone = validTimezone(request.timezone());
        Business business = context.business();
        business.updateProfile(request.name(), request.businessType());
        business.updateContactDetails(request.email(), request.mobile());
        business.updateAddress(
                request.addressLine(),
                request.city(),
                request.state(),
                request.postalCode(),
                request.countryCode(),
                timezone);
        return businessMapper.toProfileResponse(business);
    }

    private static String validTimezone(String timezone) {
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException exception) {
            throw new ApplicationException(ErrorCode.INVALID_TIMEZONE);
        }
    }
}
