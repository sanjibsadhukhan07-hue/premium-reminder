package com.premiumreminder.service;

import com.premiumreminder.model.Policy;
import com.premiumreminder.model.PolicyMarkPaidToken;
import com.premiumreminder.repository.PolicyMarkPaidTokenRepository;
import com.premiumreminder.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Generates and validates single-use, expiring links that let a customer (or admin)
 * mark a policy's premium as paid without logging in.
 *
 * A GET on the link must NOT mutate anything - WhatsApp's own crawler fetches the URL
 * server-side to build a link preview, which would silently burn the token before
 * anyone ever taps it. So GET only previews/validates (see PublicPolicyController);
 * the actual mark-as-paid only happens on the POST from the landing page's
 * auto-submitted form.
 *
 * The policyId that appears in the link path (see createMarkPaidLink) is cosmetic
 * only, for a readable URL - preview()/confirm() resolve the policy strictly from
 * the token itself, so a tampered path segment can't redirect the action onto a
 * different policy.
 *
 * Note: confirm() marks the policy paid but does NOT advance nextDueDate - see
 * PolicyService.markAlreadyPaid(). The date only moves forward once the renewal
 * date actually arrives, via PolicyService.rolloverPaidPolicies() in the daily
 * scheduler job. Any confirmation-page/WhatsApp copy should say "marked as paid",
 * not "due date changed to ----", since the date genuinely hasn't changed yet
 * at the point this returns.
 */
@Service
@RequiredArgsConstructor
public class PolicyPaymentLinkService {

    private final PolicyMarkPaidTokenRepository tokenRepository;
    private final PolicyRepository policyRepository;
    private final PolicyService policyService;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    public enum Status { VALID, USED, EXPIRED, INVALID, SUCCESS }

    public record TokenResult(Status status, Policy policy) {}

    @Transactional
    public String createMarkPaidLink(Long policyId) {
        String token = UUID.randomUUID().toString().replace("-", "");

        PolicyMarkPaidToken t = new PolicyMarkPaidToken();
        t.setToken(token);
        t.setPolicyId(policyId);
        // Covers the full +/-30-day reminder window plus buffer, so a slightly late
        // click still works.
        t.setExpiresAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).plusDays(45));
        tokenRepository.save(t);

        return publicBaseUrl + "/policies/" + policyId + "/mark-paid/" + token;
    }

    public TokenResult preview(String token) {
        var opt = tokenRepository.findByToken(token);
        if (opt.isEmpty()) {
            return new TokenResult(Status.INVALID, null);
        }
        PolicyMarkPaidToken t = opt.get();
        if (t.isUsed()) {
            return new TokenResult(Status.USED, null);
        }
        if (t.getExpiresAt().isBefore(LocalDateTime.now(ZoneId.of("Asia/Kolkata")))) {
            return new TokenResult(Status.EXPIRED, null);
        }

        Policy policy = policyRepository.findById(t.getPolicyId()).orElse(null);
        if (policy == null) {
            return new TokenResult(Status.INVALID, null);
        }
        return new TokenResult(Status.VALID, policy);
    }

    @Transactional
    public TokenResult confirm(String token) {
        var opt = tokenRepository.findByToken(token);
        if (opt.isEmpty()) {
            return new TokenResult(Status.INVALID, null);
        }
        PolicyMarkPaidToken t = opt.get();
        if (t.isUsed()) {
            return new TokenResult(Status.USED, null);
        }
        if (t.getExpiresAt().isBefore(LocalDateTime.now(ZoneId.of("Asia/Kolkata")))) {
            return new TokenResult(Status.EXPIRED, null);
        }

        Policy policy = policyRepository.findById(t.getPolicyId()).orElse(null);
        if (policy == null) {
            return new TokenResult(Status.INVALID, null);
        }

        // Delegates the actual mutation - keeps paid/lastPaidDate bookkeeping in one
        // place rather than duplicating it here.
        policyService.markAlreadyPaid(t.getPolicyId());

        // Re-fetch so the returned policy reflects the current state (paid=true,
        // lastPaidDate set) even though nextDueDate itself is unchanged for now.
        Policy updated = policyRepository.findById(t.getPolicyId()).orElseThrow();

        t.setUsed(true);
        t.setUsedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        tokenRepository.save(t);

        return new TokenResult(Status.SUCCESS, updated);
    }
}