package com.premiumreminder.repository;

import com.premiumreminder.model.CustomerRelative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomerRelativeRepository extends JpaRepository<CustomerRelative, Long> {

    List<CustomerRelative> findByCustomerId(Long customerId);

    // Eager-fetch the linked customer so the admin list page can show the
    // policyholder's name/policy number without an N+1 per row
    @Query("SELECT r FROM CustomerRelative r JOIN FETCH r.customer ORDER BY r.fullName")
    List<CustomerRelative> findAllWithCustomer();
}