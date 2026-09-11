package com.premiumreminder.service;

import com.premiumreminder.model.Customer;
import com.premiumreminder.model.Policy;
import com.premiumreminder.repository.BirthdayLogRepository;
import com.premiumreminder.repository.CustomerRepository;
import com.premiumreminder.repository.ReminderLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final BirthdayLogRepository birthdayLogRepository;

    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    public Customer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
    }

    /**
     * Applies edits from the customer form onto the persisted record. The form only
     * carries personal-details fields now that policies (and their office tags) live
     * on their own entity, so this simply copies name/contact/DOB/active across.
     *
     * WhatsApp number is optional on the form: if left blank, it's set equal to the
     * primary phone number, so the customer only needs to fill it in when it's
     * genuinely different.
     *
     * Email is also optional. HTML forms submit a blank field as "" rather than null,
     * but the email column has a unique constraint - two customers both saved with ""
     * would violate it, even though neither has a "real" duplicate email (PostgreSQL's
     * unique constraint treats every NULL as distinct from every other NULL, but treats
     * "" as a real, comparable value). So any blank/whitespace-only submission is
     * normalized to null here before it ever reaches the database. Non-blank emails
     * are lowercased, since the same address typed in different casing (e.g. a mobile
     * autocapitalizing the first letter) should still be treated as the same email.
     *
     * Uniqueness here is (full name, phone) together, NOT phone alone - one phone
     * number is often shared by a household (spouse/children), so several distinct
     * customers can legitimately share a number. What shouldn't repeat is the exact
     * same name against the exact same phone, which almost always means the same
     * person was entered/imported twice.
     */
    @Transactional
    public Customer save(Customer formCustomer) {
        String whatsapp = (formCustomer.getWhatsappNumber() == null || formCustomer.getWhatsappNumber().isBlank())
                ? formCustomer.getPhone()
                : formCustomer.getWhatsappNumber().trim();

        String email = (formCustomer.getEmail() == null || formCustomer.getEmail().isBlank())
                ? null
                : formCustomer.getEmail().trim().toLowerCase(Locale.ROOT);

        if (formCustomer.getId() == null) {
            if (customerRepository.existsByFullNameIgnoreCaseAndPhone(formCustomer.getFullName(), formCustomer.getPhone())) {
                throw new IllegalStateException(
                        "A customer named " + formCustomer.getFullName() + " with this phone number already exists.");
            }
            formCustomer.setWhatsappNumber(whatsapp);
            formCustomer.setEmail(email);
            return customerRepository.save(formCustomer);
        }

        if (customerRepository.existsByFullNameIgnoreCaseAndPhoneAndIdNot(
                formCustomer.getFullName(), formCustomer.getPhone(), formCustomer.getId())) {
            throw new IllegalStateException(
                    "Another customer named " + formCustomer.getFullName() + " with this phone number already exists.");
        }

        Customer existing = findById(formCustomer.getId());
        existing.setFullName(formCustomer.getFullName());
        existing.setEmail(email);
        existing.setPhone(formCustomer.getPhone());
        existing.setWhatsappNumber(whatsapp);
        existing.setDateOfBirth(formCustomer.getDateOfBirth());
        existing.setMessageLanguage(formCustomer.getMessageLanguage());
        existing.setActive(formCustomer.isActive());

        return customerRepository.save(existing);
    }

    @Transactional
    public String delete(Long id) {
        Customer customer = findById(id);
        String name = customer.getFullName();
        List<Long> policyIds = customer.getPolicies().stream().map(Policy::getId).toList();
        if (!policyIds.isEmpty()) {
            reminderLogRepository.deleteAllByPolicyIdIn(policyIds);
        }
        customerRepository.delete(customer);
        return name;
    }

    public List<Customer> search(String q) {
        if (q == null || q.isBlank()) {
            return findAll();
        }
        return customerRepository.search(q.trim());
    }

    @Transactional
    public void deleteAll(List<Long> ids) {
        List<Customer> customers = customerRepository.findAllById(ids);
        List<Long> policyIds = customers.stream()
                .flatMap(c -> c.getPolicies().stream())
                .map(Policy::getId)
                .toList();
        if (!policyIds.isEmpty()) {
            reminderLogRepository.deleteAllByPolicyIdIn(policyIds);
        }
        customerRepository.deleteAll(customers);
    }
}