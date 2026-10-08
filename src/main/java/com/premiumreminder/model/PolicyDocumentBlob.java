package com.premiumreminder.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Holds the raw PDF bytes of a policy document in its own table, so ordinary Policy
 * queries (dashboard, scheduler, reminders) never pull megabytes of PDF data into memory.
 * The file name and content type stay on Policy; only the bytes live here.
 * Shares its primary key with the owning Policy (one blob per policy).
 */
@Entity
@Table(name = "policy_document_blob")
@Getter
@Setter
@NoArgsConstructor
public class PolicyDocumentBlob {

    @Id
    private Long policyId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Policy policy;

    @Column(nullable = false)
    private byte[] data;
}