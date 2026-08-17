package com.thetimelessvault.watch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SetWatchRepository extends JpaRepository<SetWatch, UUID> {
    Optional<SetWatch> findBySetNumberIgnoreCase(String setNumber);

    Optional<SetWatch> findByCatalogItemId(UUID catalogItemId);

    @Query("select w from SetWatch w join fetch w.catalogItem where w.id = :id")
    Optional<SetWatch> findWithCatalogById(@Param("id") UUID id);

    @Query("select w from SetWatch w join fetch w.catalogItem where w.enabled = true order by w.setNumber")
    List<SetWatch> findEnabledWithCatalog();

    @Query("select w from SetWatch w join fetch w.catalogItem order by w.setNumber")
    List<SetWatch> findAllWithCatalog();
}
