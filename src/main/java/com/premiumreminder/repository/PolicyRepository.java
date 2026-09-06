package com.premiumreminder.repository;

import com.premiumreminder.model.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    Optional<Policy> findByPolicyNumber(String policyNumber);

    // All active, unpaid policies - the +/- 30 day window check happens in the service layer
    List<Policy> findByActiveTrueAndPaidFalse();

    // Dashboard listing, nearest renewal date first (soonest-due / most overdue-looking first)
    @Query("SELECT p FROM Policy p JOIN FETCH p.customer ORDER BY p.nextDueDate ASC")
    List<Policy> findAllOrderByNextDueDateAsc();

    List<Policy> findByCustomerId(Long customerId);
}
