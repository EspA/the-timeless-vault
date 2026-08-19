package com.thetimelessvault.config;

import com.thetimelessvault.identity.AppUser;
import com.thetimelessvault.identity.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppProperties properties;
    private final AppUserRepository users;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    public SecurityConfig(
            AppProperties properties,
            AppUserRepository users,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations
    ) {
        this.properties = properties;
        this.users = users;
        this.clientRegistrations = clientRegistrations;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/login/**",
                                "/oauth2/**",
                                "/api/auth/status",
                                "/internal/jobs/**",
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/favicon.ico",
                                "/inventory",
                                "/inventory/**",
                                "/watches",
                                "/watches/**",
                                "/market",
                                "/market/**",
                                "/scans",
                                "/scan-logs",
                                "/alerts",
                                "/buying-opportunities",
                                "/listing-logs",
                                "/settings"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpStatus.NO_CONTENT.value()))
                );

        if (properties.getSecurity().isDevBypass()) {
            http.addFilterBefore(new DevBypassFilter(properties, users), UsernamePasswordAuthenticationFilter.class);
        } else if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService()))
                    .defaultSuccessUrl("/", false)
            );
        }

        http.addFilterBefore(new InternalJobFilter(properties), BasicAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(properties.getFrontendOrigin(), properties.getBaseUrl()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        OidcUserService delegate = new OidcUserService();
        return request -> {
            OidcUser oidcUser = delegate.loadUser(request);
            String email = oidcUser.getEmail();
            if (email == null || !properties.allowedEmailList().contains(email.toLowerCase())) {
                throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "Email is not allowlisted");
            }
            AppUser user = users.findByEmailIgnoreCase(email).orElseGet(() ->
                    users.save(AppUser.create(oidcUser.getSubject(), email, oidcUser.getFullName(), oidcUser.getPicture()))
            );
            user.setName(oidcUser.getFullName());
            user.setPictureUrl(oidcUser.getPicture());
            user.setLastLoginAt(Instant.now());
            users.save(user);
            return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), oidcUser.getIdToken(), oidcUser.getUserInfo());
        };
    }

    static class DevBypassFilter extends OncePerRequestFilter {
        private final AppProperties properties;
        private final AppUserRepository users;

        DevBypassFilter(AppProperties properties, AppUserRepository users) {
            this.properties = properties;
            this.users = users;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String email = properties.allowedEmailList().stream().findFirst().orElse("dev@thetimelessvault.com");
                AppUser user = users.findByEmailIgnoreCase(email)
                        .or(() -> users.findByGoogleSub("dev-bypass"))
                        .orElseGet(() -> users.save(AppUser.create("dev-bypass", email, "Local Operator", null)));
                var auth = new UsernamePasswordAuthenticationToken(user.getEmail(), "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            chain.doFilter(request, response);
        }
    }

    static class InternalJobFilter extends OncePerRequestFilter {
        private final AppProperties properties;

        InternalJobFilter(AppProperties properties) {
            this.properties = properties;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (request.getRequestURI().startsWith("/internal/jobs/")) {
                String token = request.getHeader("X-Internal-Token");
                if (token == null || !token.equals(properties.getInternalJobToken())) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    return;
                }
                var auth = new UsernamePasswordAuthenticationToken("scheduler", "N/A", List.of(new SimpleGrantedAuthority("ROLE_JOB")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            chain.doFilter(request, response);
        }
    }
}
