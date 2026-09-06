package com.premiumreminder.repository;

import com.premiumreminder.model.PolicyMarkPaidToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicyMarkPaidTokenRepository extends JpaRepository<PolicyMarkPaidToken, Long> {
    Optional<PolicyMarkPaidToken> findByToken(String token);
}