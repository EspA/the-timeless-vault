package com.thetimelessvault.identity;

import com.thetimelessvault.config.AppProperties;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final AppProperties properties;

    public AuthController(AppUserRepository users, AppProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @GetMapping("/status")
    public Map<String, Object> status(Authentication authentication) {
        boolean authenticated = authentication != null && authentication.isAuthenticated();
        return Map.of(
                "authenticated", authenticated,
                "devBypass", properties.getSecurity().isDevBypass(),
                "loginUrl", "/oauth2/authorization/google"
        );
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        String email = authentication.getName();
        AppUser user = users.findByEmailIgnoreCase(email).orElse(null);
        return Map.of(
                "email", email,
                "name", user == null || user.getName() == null ? email : user.getName(),
                "pictureUrl", user == null || user.getPictureUrl() == null ? "" : user.getPictureUrl()
        );
    }
}
