package com.premiumreminder.repository;

import com.premiumreminder.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByPolicyNumber(String policyNumber);

    Optional<Customer> findByEmail(String email);

    // All active, unpaid customers - reminder window filtering (per-customer) happens in the service layer
    List<Customer> findByActiveTrueAndPaidFalse();

    @Query("""
    SELECT c FROM Customer c
    WHERE LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
       OR LOWER(c.policyNumber) LIKE LOWER(CONCAT('%', :q, '%'))
       OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%'))
       OR c.phone LIKE CONCAT('%', :q, '%')
    """)
    List<Customer> search(@Param("q") String q);
}
