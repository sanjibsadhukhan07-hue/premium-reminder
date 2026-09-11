package com.premiumreminder.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrity(DataIntegrityViolationException ex,
                                      jakarta.servlet.http.HttpServletRequest request,
                                      Model model) {
        model.addAttribute("errorTitle", "Could not complete action");
        if (request.getRequestURI().endsWith("/delete")) {
            model.addAttribute("errorMessage",
                    "This record can't be deleted because other data (reminder logs, birthday logs, "
                            + "or a login) still references it. Remove those first, or ask an admin to check the constraint.");
        } else {
            model.addAttribute("errorMessage",
                    "That email or phone number is already in use by another customer, or a required value is missing.");
        }
        return "error/error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneric(Exception ex, Model model) {
        model.addAttribute("errorTitle", "Something went wrong");
        model.addAttribute("errorMessage", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred.");
        return "error/error";
    }
}