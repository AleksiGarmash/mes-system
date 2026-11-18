package com.mes.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Имя текущего пользователя из JWT (preferred_username, иначе subject); вне запроса — «system». */
@Component
public class CurrentUser {

    public String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "system";
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String preferred = jwt.getClaimAsString("preferred_username");
            return preferred != null ? preferred : jwt.getSubject();
        }
        return auth.getName();
    }
}
