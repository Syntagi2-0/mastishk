package com.syntagi.servicecatalog.controller;

import com.syntagi.common.api.ApiResponse;
import com.syntagi.servicecatalog.dto.response.PublicBusinessResponse;
import com.syntagi.servicecatalog.service.PublicServiceQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

@RestController
@SecurityRequirements
@RequestMapping("/api/public/businesses")
public class PublicBusinessController {

    private final PublicServiceQueryService service;

    public PublicBusinessController(PublicServiceQueryService service) {
        this.service = service;
    }

    @GetMapping("/{publicQueueCode}")
    public ApiResponse<PublicBusinessResponse> business(@PathVariable String publicQueueCode) {
        return ApiResponse.success(service.business(publicQueueCode));
    }
}
