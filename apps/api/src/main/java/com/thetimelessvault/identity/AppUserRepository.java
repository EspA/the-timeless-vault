package com.thetimelessvault.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByGoogleSub(String googleSub);

    Optional<AppUser> findByEmailIgnoreCase(String email);
}
