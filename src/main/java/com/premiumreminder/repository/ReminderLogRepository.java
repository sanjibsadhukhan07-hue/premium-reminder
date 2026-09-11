package com.premiumreminder.repository;

import com.premiumreminder.model.ReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {
    List<ReminderLog> findTop200ByOrderBySentAtDesc();

    List<ReminderLog> findByPolicyIdOrderBySentAtDesc(Long policyId);

    void deleteAllByPolicyIdIn(List<Long> policyIds);
}
