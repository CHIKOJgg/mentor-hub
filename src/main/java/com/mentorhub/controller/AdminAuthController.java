package com.mentorhub.controller;

import com.mentorhub.dto.AdminLoginRequest;
import com.mentorhub.dto.AdminSessionDto;
import com.mentorhub.dto.CsrfTokenResponse;
import com.mentorhub.entity.AdminToken;
import com.mentorhub.repository.AdminTokenRepository;
import com.mentorhub.security.AdminTokenHasher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AdminAuthController {
    private static final Set<String> ALLOWED_ROLES = Set.of("MENTOR", "CURATOR");

    private final AdminTokenRepository adminTokenRepository;
    private final SecurityContextRepository securityContextRepository;

    @GetMapping("/csrf")
    public CsrfTokenResponse getCsrfToken(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getToken());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody AdminLoginRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String key = requestBody.accessKey().trim();
        if (key.length() > 512) return unauthorized();

        AdminToken admin = adminTokenRepository
                .findByTokenAndIsActiveTrue(AdminTokenHasher.hash(key))
                .filter(candidate -> candidate.getExpiresAt() == null || candidate.getExpiresAt().isAfter(Instant.now()))
                .filter(candidate -> candidate.getRole() != null && ALLOWED_ROLES.contains(candidate.getRole().toUpperCase(Locale.ROOT)))
                .orElse(null);
        if (admin == null) return unauthorized();

        if (admin.getLastUsedAt() == null || admin.getLastUsedAt().isBefore(Instant.now().minusSeconds(300))) {
            admin.setLastUsedAt(Instant.now());
            adminTokenRepository.save(admin);
        }

        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) request.changeSessionId();

        String role = admin.getRole().toUpperCase(Locale.ROOT);
        PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(
                admin.getAdminName(),
                null,
                Set.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new AdminSessionDto(admin.getAdminName(), role));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("status", 401, "error", "Unauthorized", "message", "Invalid access key"));
    }
}
