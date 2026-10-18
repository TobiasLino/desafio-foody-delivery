package com.foody.delivery.rpd.infra.security;

import org.springframework.security.core.Authentication;

public record AuthenticatedUser(String email, Boolean operator) {

    public static AuthenticatedUser from(final Authentication authentication) {
        final boolean operator = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OPERATOR"));
        return new AuthenticatedUser(authentication.getName(), operator);
    }
}
