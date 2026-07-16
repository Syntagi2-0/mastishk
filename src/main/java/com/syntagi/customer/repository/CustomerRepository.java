package com.syntagi.customer.repository;

import com.syntagi.customer.entity.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByBusinessIdAndMobile(UUID businessId, String mobile);

    @EntityGraph(attributePaths = "business")
    List<Customer> findByBusinessIdAndFullNameContainingIgnoreCase(
            UUID businessId, String fullName);
}
