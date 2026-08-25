package com.thetimelessvault.settings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface ApiCallDailyRepository extends JpaRepository<ApiCallDaily, ApiCallDailyId> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO api_call_daily (day, platform, call_count)
            VALUES (:day, :platform, 1)
            ON CONFLICT (day, platform)
            DO UPDATE SET call_count = api_call_daily.call_count + 1
            """, nativeQuery = true)
    void increment(@Param("day") LocalDate day, @Param("platform") String platform);

    List<ApiCallDaily> findByDayGreaterThanEqualOrderByDayDescPlatformAsc(LocalDate day);
}
