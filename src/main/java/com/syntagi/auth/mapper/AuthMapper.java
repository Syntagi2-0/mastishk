package com.syntagi.auth.mapper;

import com.syntagi.auth.dto.response.AuthenticatedBusinessResponse;
import com.syntagi.auth.dto.response.AuthenticatedUserResponse;
import com.syntagi.auth.entity.User;
import com.syntagi.business.entity.Business;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public AuthenticatedUserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getMobile(),
                user.getStatus());
    }

    public AuthenticatedBusinessResponse toBusinessResponse(Business business) {
        if (business == null) {
            return null;
        }
        return new AuthenticatedBusinessResponse(
                business.getId(),
                business.getName(),
                business.getSlug(),
                business.getBusinessType(),
                business.getPublicQueueCode(),
                business.getStatus());
    }
}
