package com.premiumreminder.repository;

import com.premiumreminder.model.PolicyDocumentBlob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyDocumentBlobRepository extends JpaRepository<PolicyDocumentBlob, Long> {
}