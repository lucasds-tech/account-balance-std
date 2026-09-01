package com.santander.ingestor.data.repository;

import com.santander.ingestor.data.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO accounts (id, owner, status, created_at)
            VALUES (:id, :owner, :status, :createdAt)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("owner") UUID owner,
                       @Param("status") String status,
                       @Param("createdAt") Instant createdAt);
}
