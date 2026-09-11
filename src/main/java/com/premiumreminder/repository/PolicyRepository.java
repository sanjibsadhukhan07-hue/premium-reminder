package com.premiumreminder.repository;

import com.premiumreminder.model.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    Optional<Policy> findByPolicyNumber(String policyNumber);

    // All active, unpaid policies - the +/- 30 day reminder window check happens in the service layer
    List<Policy> findByActiveTrueAndPaidFalse();

    // Dashboard listing, nearest renewal date first (soonest-due / most overdue-looking first)
    @Query("SELECT p FROM Policy p JOIN FETCH p.customer ORDER BY p.nextDueDate ASC")
    List<Policy> findAllOrderByNextDueDateAsc();

    List<Policy> findByCustomerId(Long customerId);

    List<Policy> findByPaidFalseAndNextDueDateAfter(LocalDate date);

    // Rollover candidates: active + marked paid + renewal date has actually arrived
    // (or passed). Used by PolicyService.rolloverPaidPolicies() in the daily
    // scheduler job, so it's filtered in SQL rather than pulling every row.
    @Query("SELECT p FROM Policy p WHERE p.active = true AND p.paid = true AND p.nextDueDate <= :today")
    List<Policy> findRolloverCandidates(@Param("today") LocalDate today);
}