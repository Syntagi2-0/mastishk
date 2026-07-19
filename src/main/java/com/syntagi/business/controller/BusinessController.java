package com.syntagi.business.controller;

import com.syntagi.business.dto.request.UpdateBusinessRequest;
import com.syntagi.business.dto.response.BusinessProfileResponse;
import com.syntagi.business.service.BusinessProfileService;
import com.syntagi.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business")
public class BusinessController {

    private final BusinessProfileService businessProfileService;

    public BusinessController(BusinessProfileService businessProfileService) {
        this.businessProfileService = businessProfileService;
    }

    @GetMapping({"", "/me"})
    public ApiResponse<BusinessProfileResponse> getCurrentBusiness() {
        return ApiResponse.success(businessProfileService.getCurrentBusiness());
    }

    @PutMapping({"", "/me"})
    public ApiResponse<BusinessProfileResponse> updateCurrentBusiness(
            @Valid @RequestBody UpdateBusinessRequest request) {
        return ApiResponse.success(
                businessProfileService.updateCurrentBusiness(request),
                "Business profile updated successfully");
    }
}
