package com.syntagi.customer.controller;

import com.syntagi.common.api.ApiResponse;
import com.syntagi.customer.dto.response.CustomerResponse;
import com.syntagi.customer.service.CustomerQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerQueryService service;

    public CustomerController(CustomerQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Page<CustomerResponse>> list(
            @RequestParam(required = false) String search, Pageable pageable) {
        return ApiResponse.success(service.list(search, pageable));
    }
}
