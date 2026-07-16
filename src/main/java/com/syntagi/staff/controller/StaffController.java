package com.syntagi.staff.controller;

import com.syntagi.auth.enums.BusinessUserStatus;
import com.syntagi.common.api.ApiResponse;
import com.syntagi.staff.dto.request.CreateStaffRequest;
import com.syntagi.staff.dto.request.UpdateStaffStatusRequest;
import com.syntagi.staff.dto.response.StaffMemberResponse;
import com.syntagi.staff.dto.response.StaffMembershipResponse;
import com.syntagi.staff.service.StaffService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StaffMemberResponse>> createStaff(
            @Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(staffService.createStaff(request), "Staff member created"));
    }

    @GetMapping
    public ApiResponse<List<StaffMemberResponse>> listStaff(
            @RequestParam(required = false) BusinessUserStatus status) {
        return ApiResponse.success(staffService.listStaff(status));
    }

    @PutMapping("/{businessUserId}/status")
    public ApiResponse<StaffMemberResponse> updateStatus(
            @PathVariable UUID businessUserId,
            @Valid @RequestBody UpdateStaffStatusRequest request) {
        return ApiResponse.success(
                staffService.updateStatus(businessUserId, request),
                "Staff membership status updated");
    }

    @GetMapping("/me")
    public ApiResponse<StaffMembershipResponse> currentMembership() {
        return ApiResponse.success(staffService.currentMembership());
    }
}
