package com.boxue.api.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthRepository authRepository;

    public JwtAuthenticationFilter(JwtService jwtService, AuthRepository authRepository) {
        this.jwtService = jwtService;
        this.authRepository = authRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            Claims claims = jwtService.tryParse(token);
            if (claims != null) {
                authenticate(claims);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims) {
        try {
            authenticateUnsafe(claims);
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticateUnsafe(Claims claims) {
        String sid = claims.get("sid", String.class);
        if (sid == null) {
            return;
        }
        UUID sessionId = UUID.fromString(sid);
        if (!authRepository.isSessionActive(sessionId)) {
            return;
        }

        UUID userId = UUID.fromString(claims.getSubject());
        String deviceIdClaim = claims.get("deviceId", String.class);
        UUID deviceId = deviceIdClaim == null ? null : UUID.fromString(deviceIdClaim);

        List<String> roles = stringList(claims.get("roles"));
        List<String> permissions = stringList(claims.get("permissions"));

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        for (String permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        String audience = claims.getAudience() == null
            ? "client" : claims.getAudience().stream().findFirst().orElse("client");
        authorities.add(new SimpleGrantedAuthority("AUD_" + audience));

        AuthPrincipal principal = new AuthPrincipal(
            userId,
            sessionId,
            deviceId,
            audience,
            roles,
            permissions);

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }
}
