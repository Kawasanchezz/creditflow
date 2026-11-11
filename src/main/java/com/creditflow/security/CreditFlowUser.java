package com.creditflow.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** customerId e nulo para analistas. */
public class CreditFlowUser extends User {
    private final Long customerId;

    public CreditFlowUser(String username, String password, Collection<? extends GrantedAuthority> authorities,
                          Long customerId) {
        super(username, password, authorities);
        this.customerId = customerId;
    }

    public Long getCustomerId() {
        return customerId;
    }
}
