package com.syntagi.servicecatalog.controller;

import com.syntagi.common.api.ApiResponse;
import com.syntagi.servicecatalog.dto.request.ServiceStatusRequest;
import com.syntagi.servicecatalog.dto.request.ServiceUpsertRequest;
import com.syntagi.servicecatalog.dto.response.ServiceResponse;
import com.syntagi.servicecatalog.service.ServiceCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceCatalogService serviceCatalogService;

    public ServiceController(ServiceCatalogService serviceCatalogService) {
        this.serviceCatalogService = serviceCatalogService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ServiceResponse>> create(
            @Valid @RequestBody ServiceUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(serviceCatalogService.create(request), "Service created"));
    }

    @GetMapping
    public ApiResponse<List<ServiceResponse>> list(
            @RequestParam(required = false) Boolean active) {
        return ApiResponse.success(serviceCatalogService.list(active));
    }

    @GetMapping("/{serviceId}")
    public ApiResponse<ServiceResponse> get(@PathVariable UUID serviceId) {
        return ApiResponse.success(serviceCatalogService.get(serviceId));
    }

    @PutMapping("/{serviceId}")
    public ApiResponse<ServiceResponse> update(
            @PathVariable UUID serviceId,
            @Valid @RequestBody ServiceUpsertRequest request) {
        return ApiResponse.success(serviceCatalogService.update(serviceId, request), "Service updated");
    }

    @PatchMapping("/{serviceId}/status")
    public ApiResponse<ServiceResponse> updateStatus(
            @PathVariable UUID serviceId,
            @Valid @RequestBody ServiceStatusRequest request) {
        return ApiResponse.success(
                serviceCatalogService.updateStatus(serviceId, request),
                "Service status updated");
    }
}
