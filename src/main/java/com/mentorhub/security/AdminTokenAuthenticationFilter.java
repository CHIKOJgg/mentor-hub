package com.mentorhub.security;

import com.mentorhub.entity.AdminToken;
import com.mentorhub.repository.AdminTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminTokenAuthenticationFilter extends OncePerRequestFilter {

    private final AdminTokenRepository adminTokenRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            Optional<AdminToken> adminTokenOpt = adminTokenRepository.findByTokenAndIsActiveTrue(token);

            if (adminTokenOpt.isPresent()) {
                AdminToken adminToken = adminTokenOpt.get();

                // Check expiration if set
                if (adminToken.getExpiresAt() != null && adminToken.getExpiresAt().isBefore(Instant.now())) {
                    log.warn("Admin token expired for user: {}", adminToken.getAdminName());
                } else {
                    // Update last used timestamp
                    adminToken.setLastUsedAt(Instant.now());
                    try {
                        adminTokenRepository.save(adminToken);
                    } catch (Exception e) {
                        log.debug("Could not update lastUsedAt for admin token: {}", e.getMessage());
                    }

                    // Format authority role
                    String rawRole = adminToken.getRole();
                    String role = rawRole.startsWith("ROLE_") ? rawRole : "ROLE_" + rawRole;
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role);

                    PreAuthenticatedAuthenticationToken authentication =
                            new PreAuthenticatedAuthenticationToken(
                                    adminToken.getAdminName(),
                                    token,
                                    Collections.singletonList(authority)
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Authenticated admin '{}' with role '{}'", adminToken.getAdminName(), role);
                }
            } else {
                log.debug("Invalid or inactive admin token presented: {}", token);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader("X-Admin-Token");
        if (StringUtils.hasText(token)) {
            return token.trim();
        }

        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        String paramToken = request.getParameter("token");
        if (StringUtils.hasText(paramToken)) {
            return paramToken.trim();
        }

        return null;
    }
}
