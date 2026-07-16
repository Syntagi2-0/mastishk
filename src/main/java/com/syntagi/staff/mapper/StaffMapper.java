package com.syntagi.staff.mapper;

import com.syntagi.staff.dto.response.StaffMemberResponse;
import com.syntagi.staff.dto.response.StaffMembershipResponse;
import com.syntagi.staff.entity.BusinessUser;
import org.springframework.stereotype.Component;

@Component
public class StaffMapper {

    public StaffMemberResponse toMemberResponse(BusinessUser membership) {
        if (membership == null) {
            return null;
        }
        return new StaffMemberResponse(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getFullName(),
                membership.getUser().getEmail(),
                membership.getUser().getMobile(),
                membership.getUser().getStatus(),
                membership.getRole(),
                membership.getStatus());
    }

    public StaffMembershipResponse toMembershipResponse(BusinessUser membership) {
        if (membership == null) {
            return null;
        }
        return new StaffMembershipResponse(
                membership.getId(),
                membership.getUser().getId(),
                membership.getBusiness().getId(),
                membership.getBusiness().getName(),
                membership.getRole(),
                membership.getStatus());
    }
}
