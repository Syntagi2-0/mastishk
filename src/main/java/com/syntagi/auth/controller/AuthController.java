package com.syntagi.auth.controller;

import com.syntagi.auth.dto.request.LoginRequest;
import com.syntagi.auth.dto.request.RegisterOwnerRequest;
import com.syntagi.auth.dto.response.AuthResponse;
import com.syntagi.auth.dto.response.CurrentUserResponse;
import com.syntagi.auth.service.AuthService;
import com.syntagi.common.api.ApiResponse;
import com.syntagi.common.security.SyntagiPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register-owner")
    public ResponseEntity<ApiResponse<AuthResponse>> registerOwner(
            @Valid @RequestBody RegisterOwnerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.registerOwner(request), "Owner registered successfully"));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> currentUser(
            @AuthenticationPrincipal SyntagiPrincipal principal) {
        return ApiResponse.success(authService.currentUser(principal));
    }
}
