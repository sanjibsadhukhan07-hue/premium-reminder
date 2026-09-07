package com.premiumreminder.repository;

import com.premiumreminder.model.BirthdayCardTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BirthdayCardTemplateRepository extends JpaRepository<BirthdayCardTemplate, Long> {
}