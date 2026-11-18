package com.mes.controller;

import com.mes.dto.response.MeResponse;
import com.mes.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Кто я: имя и роли из JWT. Интерфейс использует это, чтобы показывать только доступные разделы. */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        var roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
        return ResponseEntity.ok(new MeResponse(currentUser.username(), roles));
    }
}
