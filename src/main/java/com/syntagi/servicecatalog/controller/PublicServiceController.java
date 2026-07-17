package com.syntagi.servicecatalog.controller;

import com.syntagi.common.api.ApiResponse;
import com.syntagi.servicecatalog.dto.response.PublicServiceResponse;
import com.syntagi.servicecatalog.service.PublicServiceQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

@RestController
@SecurityRequirements
@RequestMapping("/api/public/businesses/{publicQueueCode}/services")
public class PublicServiceController {

    private final PublicServiceQueryService publicServiceQueryService;

    public PublicServiceController(PublicServiceQueryService publicServiceQueryService) {
        this.publicServiceQueryService = publicServiceQueryService;
    }

    @GetMapping
    public ApiResponse<List<PublicServiceResponse>> listActiveServices(
            @PathVariable String publicQueueCode) {
        return ApiResponse.success(publicServiceQueryService.listActiveServices(publicQueueCode));
    }
}
