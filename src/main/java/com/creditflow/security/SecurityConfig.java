package com.creditflow.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * API stateless com HTTP Basic: sem sessao e sem cookies emitidos.
 * A protecao CSRF continua ativa para qualquer requisicao que possa carregar credencial ambiente do navegador:
 * so e dispensada quando a credencial vai explicita no cabecalho Authorization (um site de terceiros nao
 * consegue defini-lo) ou quando a requisicao nao traz cookie nenhum. O desafio WWW-Authenticate e suprimido
 * para que o navegador nunca guarde a credencial Basic e a reenvie sozinho.
 * Papeis: ANALYST (acesso total, decide analise manual) e CUSTOMER (apenas as proprias propostas).
 */
@Configuration
public class SecurityConfig {

    private static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

    private static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=()";
    private static final long MAX_BODY_BYTES = 64 * 1024;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthFailureThrottle throttle) throws Exception {
        RequestMatcher noAmbientCredentials = (HttpServletRequest r) ->
                r.getHeader(HttpHeaders.AUTHORIZATION) != null || r.getCookies() == null;
        RequestMatcher apiDocs = (HttpServletRequest r) ->
                r.getRequestURI().startsWith("/swagger-ui") || r.getRequestURI().startsWith("/v3/api-docs");
        HeaderWriter csp = new DelegatingRequestMatcherHeaderWriter(
                (HttpServletRequest r) -> !apiDocs.matches(r), new StaticHeadersWriter("Content-Security-Policy", CSP));

        http.csrf(c -> c.ignoringRequestMatchers(noAmbientCredentials))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new RequestSizeLimitFilter(MAX_BODY_BYTES), BasicAuthenticationFilter.class)
                .addFilterBefore(new AuthThrottleFilter(throttle), BasicAuthenticationFilter.class)
                .headers(h -> h.referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)).addHeaderWriter(csp)
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/index.html", "/favicon.svg", "/assets/**", "/error", "/api/demo").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/proposals/*/manual-decision", "/api/proposals/*/outcomes").hasRole("ANALYST")
                        .requestMatchers("/api/outcomes/**").hasRole("ANALYST")
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
