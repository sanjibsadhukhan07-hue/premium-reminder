package com.premiumreminder.config;

import com.premiumreminder.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Bounces a logged-in user to /account/change-password if their User.mustChangePassword
 * flag is still set (fresh admin seed or a just-created customer login) - a temporary
 * password shouldn't be usable to browse the rest of the app first.
 */
@Component
@RequiredArgsConstructor
public class ForcePasswordChangeFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "/account/change-password", "/logout", "/css", "/images");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String uri = request.getRequestURI();
        boolean allowed = ALLOWED_PREFIXES.stream().anyMatch(uri::startsWith);

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()) && !allowed) {
            boolean mustChange = userRepository.findByUsername(auth.getName())
                    .map(u -> u.isMustChangePassword())
                    .orElse(false);
            if (mustChange) {
                response.sendRedirect(request.getContextPath() + "/account/change-password");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
