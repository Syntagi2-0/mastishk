package com.syntagi.business.mapper;

import com.syntagi.business.dto.response.BusinessProfileResponse;
import com.syntagi.business.entity.Business;
import org.springframework.stereotype.Component;

@Component
public class BusinessMapper {

    public BusinessProfileResponse toProfileResponse(Business business) {
        if (business == null) {
            return null;
        }
        return new BusinessProfileResponse(
                business.getId(),
                business.getName(),
                business.getSlug(),
                business.getBusinessType(),
                business.getEmail(),
                business.getMobile(),
                business.getAddressLine(),
                business.getCity(),
                business.getState(),
                business.getPostalCode(),
                business.getCountryCode(),
                business.getTimezone(),
                business.getPublicQueueCode(),
                business.getStatus());
    }
}
