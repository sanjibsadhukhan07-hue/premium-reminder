package com.premiumreminder.service;

import com.premiumreminder.model.Customer;
import com.premiumreminder.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    public Customer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
    }

    /**
     * Applies edits from the customer form onto the persisted record. The form only
     * carries personal-details fields now that policies live on their own entity, so
     * this simply copies name/contact/DOB/office-tag/active across.
     *
     * WhatsApp number is optional on the form: if left blank, it's set equal to the
     * primary phone number, so the customer only needs to fill it in when it's
     * genuinely different.
     */
    @Transactional
    public Customer save(Customer formCustomer) {
        String whatsapp = (formCustomer.getWhatsappNumber() == null || formCustomer.getWhatsappNumber().isBlank())
                ? formCustomer.getPhone()
                : formCustomer.getWhatsappNumber().trim();

        if (formCustomer.getId() == null) {
            formCustomer.setWhatsappNumber(whatsapp);
            return customerRepository.save(formCustomer);
        }

        Customer existing = findById(formCustomer.getId());
        existing.setFullName(formCustomer.getFullName());
        existing.setEmail(formCustomer.getEmail());
        existing.setPhone(formCustomer.getPhone());
        existing.setWhatsappNumber(whatsapp);
        existing.setDateOfBirth(formCustomer.getDateOfBirth());
        existing.setOfficeTag(formCustomer.getOfficeTag());
        existing.setMessageLanguage(formCustomer.getMessageLanguage());
        existing.setActive(formCustomer.isActive());

        return customerRepository.save(existing);
    }

    public void delete(Long id) {
        customerRepository.deleteById(id);
    }

    public List<Customer> search(String q) {
        if (q == null || q.isBlank()) {
            return findAll();
        }
        return customerRepository.search(q.trim());
    }

    public void deleteAll(List<Long> ids) {
        customerRepository.deleteAllById(ids);
    }
}