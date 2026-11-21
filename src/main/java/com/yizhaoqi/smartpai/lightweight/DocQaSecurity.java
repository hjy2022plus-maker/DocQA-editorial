package com.yizhaoqi.smartpai.lightweight;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Configuration
class DocQaSecurity {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    Tokens tokens(@Value("${app.jwt-secret}") String encoded) {
        if (encoded.isBlank()) throw new IllegalStateException("请配置 JWT_SECRET_KEY（至少 32 字节随机数据的 Base64）");
        return new Tokens(Keys.hmacShaKeyFor(Base64.getDecoder().decode(encoded)));
    }

    @Bean
    SecurityFilterChain access(HttpSecurity http, Tokens tokens, DocQaAccountRepository accounts) throws Exception {
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, error) ->
                        response.sendError(401, "请先登录")))
                .authorizeHttpRequests(a -> a.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/", "/api/v1/users/login", "/api/v1/users/register",
                                "/api/v1/auth/refreshToken").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN").anyRequest().authenticated())
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                            throws IOException, ServletException {
                        String auth = req.getHeader("Authorization");
                        if (auth != null && auth.startsWith("Bearer ")) {
                            tokens.user(auth.substring(7), "access").flatMap(accounts::findById).ifPresent(account -> {
                                var authority = new SimpleGrantedAuthority("ROLE_" + account.role);
                                SecurityContextHolder.getContext().setAuthentication(
                                        new UsernamePasswordAuthenticationToken(account.id.toString(), null, List.of(authority)));
                            });
                        }
                        chain.doFilter(req, res);
                    }
                }, UsernamePasswordAuthenticationFilter.class).build();
    }

    static final class Tokens {
        private final SecretKey key;
        Tokens(SecretKey key) { this.key = key; }

        Map<String, String> issue(DocQaAccount account) {
            return Map.of("token", token(account.id, "access", Duration.ofHours(1)),
                    "refreshToken", token(account.id, "refresh", Duration.ofDays(7)));
        }

        Optional<Long> user(String token, String type) {
            try {
                var claims = Jwts.parserBuilder().setSigningKey(key).requireIssuer("docqa")
                        .require("type", type).build().parseClaimsJws(token).getBody();
                return Optional.of(Long.parseLong(claims.getSubject()));
            } catch (JwtException | IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        private String token(long id, String type, Duration duration) {
            return Jwts.builder().setIssuer("docqa").setSubject(Long.toString(id))
                    .claim("type", type).setExpiration(Date.from(Instant.now().plus(duration)))
                    .signWith(key).compact();
        }
    }
}
