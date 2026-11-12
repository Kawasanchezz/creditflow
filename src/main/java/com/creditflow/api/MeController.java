package com.creditflow.api;

import com.creditflow.security.CreditFlowUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    public record MeResponse(String username, String role, Long customerId) {
    }

    @GetMapping
    public MeResponse me(Authentication auth) {
        boolean analyst = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ANALYST"));
        Long customerId = auth.getPrincipal() instanceof CreditFlowUser u ? u.getCustomerId() : null;
        return new MeResponse(auth.getName(), analyst ? "ANALYST" : "CUSTOMER", customerId);
    }
}
