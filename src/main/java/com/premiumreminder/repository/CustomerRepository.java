package com.premiumreminder.repository;

import com.premiumreminder.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Primary matching key for bulk import - your source sheets reliably have a phone
    // number but frequently leave email blank.
    Optional<Customer> findByPhone(String phone);

    // Matches on the customer's own fields OR any of their policies' policy number /
    // insurer name, so searching a policy number finds the right person.
    @Query("""
    SELECT DISTINCT c FROM Customer c
    LEFT JOIN c.policies p
    WHERE LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
       OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%'))
       OR c.phone LIKE CONCAT('%', :q, '%')
       OR LOWER(p.policyNumber) LIKE LOWER(CONCAT('%', :q, '%'))
       OR LOWER(p.insurerName) LIKE LOWER(CONCAT('%', :q, '%'))
    """)
    List<Customer> search(@Param("q") String q);
}
