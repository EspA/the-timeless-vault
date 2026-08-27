package com.thetimelessvault.inbound;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    @Query("select distinct q from Quote q left join fetch q.lines where q.id = :id")
    Optional<Quote> findWithDetailsById(@Param("id") UUID id);

    @Query(value = "select nextval('quote_number_seq')", nativeQuery = true)
    long nextQuoteNumber();
}
