package com.premiumreminder.repository;

import com.premiumreminder.model.BirthdayLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BirthdayLogRepository extends JpaRepository<BirthdayLog, Long> {
    List<BirthdayLog> findTop200ByOrderBySentAtDesc();
}