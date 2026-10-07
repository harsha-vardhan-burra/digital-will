package com.digitalwill.auth.repository;

import com.digitalwill.auth.model.UserAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAuthTokenRepository extends JpaRepository<UserAuthToken, UUID> {
    Optional<UserAuthToken> findByTokenHash(String tokenHash);
    void deleteByUserId(UUID userId);
    void deleteByTokenHash(String tokenHash);
}
