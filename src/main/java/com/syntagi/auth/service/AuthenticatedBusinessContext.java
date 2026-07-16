package com.syntagi.auth.service;

import com.syntagi.auth.entity.User;
import com.syntagi.business.entity.Business;
import com.syntagi.staff.entity.BusinessUser;

public record AuthenticatedBusinessContext(
        User user,
        Business business,
        BusinessUser membership) {
}
