package com.premiumreminder.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final ForcePasswordChangeFilter forcePasswordChangeFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // simplify for MVP; re-enable with token support for production forms
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/images/**", "/login").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/customer/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .rememberMe(remember -> remember
                        .key("kfs-premium-reminder-remember-key")
                        .tokenValiditySeconds(1209600) // 14 days
                        .rememberMeParameter("remember-me")
                )
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
                .addFilterAfter(forcePasswordChangeFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
