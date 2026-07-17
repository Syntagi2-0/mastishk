package com.syntagi.customer.service;

import com.syntagi.auth.service.AuthenticatedBusinessContextService;
import com.syntagi.customer.dto.response.CustomerResponse;
import com.syntagi.customer.entity.Customer;
import com.syntagi.customer.repository.CustomerRepository;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerQueryService {

    private final AuthenticatedBusinessContextService contextService;
    private final CustomerRepository repository;

    public CustomerQueryService(
            AuthenticatedBusinessContextService contextService,
            CustomerRepository repository) {
        this.contextService = contextService;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(String search, Pageable pageable) {
        UUID businessId = contextService.current().business().getId();
        Specification<Customer> filters = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("business").get("id"), businessId));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(cb.lower(root.get("mobile")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Pageable safePage = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by("fullName").ascending());
        return repository.findAll(filters, safePage).map(CustomerQueryService::response);
    }

    private static CustomerResponse response(Customer customer) {
        return new CustomerResponse(
                customer.getId(), customer.getFullName(), customer.getMobile(),
                customer.getEmail(), customer.getCreatedAt());
    }
}
