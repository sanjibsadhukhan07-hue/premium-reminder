package com.premiumreminder.repository;

import com.premiumreminder.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByPolicyNumber(String policyNumber);

    Optional<Customer> findByEmail(String email);

    // All active, unpaid customers - reminder window filtering (per-customer) happens in the service layer
    List<Customer> findByActiveTrueAndPaidFalse();
}
