package com.creditflow.security;

import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * API stateless com HTTP Basic: sem sessao nem cookie, entao CSRF nao se aplica.
 * Papeis: ANALYST (acesso total, decide analise manual) e CUSTOMER (apenas as proprias propostas).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .headers(h -> h.referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/error", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/proposals/*/manual-decision").hasRole("ANALYST")
                        .requestMatchers("/api/**").hasAnyRole("ANALYST", "CUSTOMER")
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(SecurityProperties props, PasswordEncoder encoder) {
        if (props.analystPassword() == null || props.analystPassword().isBlank()) {
            throw new IllegalStateException("creditflow.security.analyst-password must be set");
        }
        Map<String, Account> accounts = new HashMap<>();
        accounts.put("analyst", new Account(encoder.encode(props.analystPassword()), "ROLE_ANALYST", null));
        props.customerPasswordsOrEmpty().forEach((id, password) ->
                accounts.put("customer-" + id, new Account(encoder.encode(password), "ROLE_CUSTOMER", id)));
        return username -> {
            Account account = accounts.get(username);
            if (account == null) {
                throw new UsernameNotFoundException("unknown user");
            }
            // instancia nova a cada chamada: o Spring apaga a senha do objeto apos autenticar
            return new CreditFlowUser(username, account.passwordHash(),
                    AuthorityUtils.createAuthorityList(account.role()), account.customerId());
        };
    }

    private record Account(String passwordHash, String role, Long customerId) {
    }
}
