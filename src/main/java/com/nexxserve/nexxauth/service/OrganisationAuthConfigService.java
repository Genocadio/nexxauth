package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.UpdateOrganisationAuthConfigRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationAuthConfigResponse;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationAuthConfig;
import com.nexxserve.nexxauth.entity.OrganisationPasswordHistory;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import com.nexxserve.nexxauth.exception.BadRequestException;
import com.nexxserve.nexxauth.repository.OrganisationAuthConfigRepository;
import com.nexxserve.nexxauth.repository.OrganisationPasswordHistoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Organisation-level authentication settings and password policy. One config
 * row per organisation (created lazily with defaults); the rules apply whenever
 * a password is set for an org user - admin create/update, org register - and
 * are enforced at org login (length, reuse history, expiration). The platform
 * auth flow is untouched: this is organisation-scoped only.
 */
@Service
public class OrganisationAuthConfigService {

    private final OrganisationAuthConfigRepository configRepository;
    private final OrganisationPasswordHistoryRepository historyRepository;
    private final EntityManager entityManager;
    private final PasswordEncoder passwordEncoder;

    public OrganisationAuthConfigService(OrganisationAuthConfigRepository configRepository,
                                         OrganisationPasswordHistoryRepository historyRepository,
                                         EntityManager entityManager,
                                         PasswordEncoder passwordEncoder) {
        this.configRepository = configRepository;
        this.historyRepository = historyRepository;
        this.entityManager = entityManager;
        this.passwordEncoder = passwordEncoder;
    }

    /** The organisation's config, lazily created with defaults if missing. The
     * creation is double-checked under a lock on the organisation row so two
     * concurrent first accesses cannot race into a unique-constraint 409. */
    @Transactional
    public OrganisationAuthConfig configOf(Organisation organisation) {
        return configRepository.findByOrganisationId(organisation.getId())
                .orElseGet(() -> createConfig(organisation));
    }

    private OrganisationAuthConfig createConfig(Organisation organisation) {
        // Serialize concurrent lazy creation for this organisation on its own
        // (always present) row, then re-check: the other transaction has either
        // committed its insert or is waiting behind us.
        entityManager.lock(managed(organisation), LockModeType.PESSIMISTIC_WRITE);
        return configRepository.findByOrganisationId(organisation.getId())
                .orElseGet(() -> {
                    OrganisationAuthConfig config = new OrganisationAuthConfig();
                    config.setOrganisation(organisation);
                    return configRepository.save(config);
                });
    }

    /** Returns a managed instance of {@code organisation} (merge is a no-op for
     * an already-managed entity and never creates a duplicate in the session). */
    private Organisation managed(Organisation organisation) {
        return entityManager.merge(organisation);
    }

    /**
     * Not read-only: a fresh org has no config row yet, so this lazily creates
     * the defaults (Postgres rejects inserts in read-only transactions).
     */
    @Transactional
    public OrganisationAuthConfigResponse get(Organisation organisation) {
        return toResponse(configOf(organisation));
    }

    @Transactional
    public OrganisationAuthConfigResponse update(Organisation organisation,
                                                 UpdateOrganisationAuthConfigRequest request) {
        OrganisationAuthConfig config = configOf(organisation);
        if (request.authType() != null) {
            config.setAuthType(request.authType());
        }
        if (request.passwordMinLength() != null) {
            config.setPasswordMinLength(request.passwordMinLength());
        }
        if (request.passwordMaxLength() != null) {
            config.setPasswordMaxLength(request.passwordMaxLength());
        }
        if (request.passwordExpirationDays() != null) {
            config.setPasswordExpirationDays(request.passwordExpirationDays());
        }
        if (request.passwordHistoryCount() != null) {
            config.setPasswordHistoryCount(request.passwordHistoryCount());
        }
        validateConfig(config);
        return toResponse(configRepository.save(config));
    }

    /**
     * Sets a user's password: validates the org's rules (length, byte bound,
     * reuse history), encodes, stamps the change time and auth type, and
     * records the previous hash in the history (trimmed to the configured
     * depth). Clears nothing - pass a blank raw to {@link #clearAuth} instead.
     */
    @Transactional
    public void setPassword(OrganisationUser user, String rawPassword) {
        OrganisationAuthConfig config = configOf(user.getOrganisation());
        validateNewPassword(config, user, rawPassword);
        String previousHash = user.getPasswordHash();
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPasswordChangedAt(Instant.now());
        if (user.getAuthType() == null) {
            user.setAuthType(config.getAuthType());
        }
        if (previousHash != null && config.getPasswordHistoryCount() > 0) {
            recordHistory(user, previousHash);
            trimHistory(user.getId(), config.getPasswordHistoryCount());
        }
    }

    /** Removes the user's auth (password + type): they can no longer log in. */
    @Transactional
    public void clearAuth(OrganisationUser user) {
        user.setPasswordHash(null);
        user.setAuthType(null);
        user.setPasswordChangedAt(null);
        historyRepository.deleteByUserIdAndIdNotIn(user.getId(), Set.of());
    }

    /** True when the password has passed the org's expiration window. */
    public boolean isPasswordExpired(OrganisationAuthConfig config, OrganisationUser user) {
        if (config.getPasswordExpirationDays() <= 0 || user.getPasswordChangedAt() == null) {
            return false;
        }
        return user.getPasswordChangedAt()
                .plus(Duration.ofDays(config.getPasswordExpirationDays()))
                .isBefore(Instant.now());
    }

    private void validateNewPassword(OrganisationAuthConfig config, OrganisationUser user, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Password is required");
        }
        int length = raw.length();
        if (length < config.getPasswordMinLength() || length > config.getPasswordMaxLength()) {
            throw new BadRequestException("Password must be between " + config.getPasswordMinLength()
                    + " and " + config.getPasswordMaxLength() + " characters");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Password must be at most 72 bytes (UTF-8)");
        }
        if (config.getPasswordHistoryCount() > 0 && matchesHistory(config, user, raw)) {
            throw new BadRequestException("Password was used recently. Choose a different password");
        }
    }

    private boolean matchesHistory(OrganisationAuthConfig config, OrganisationUser user, String raw) {
        String current = user.getPasswordHash();
        if (current != null && passwordEncoder.matches(raw, current)) {
            return true;
        }
        return historyRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .limit(config.getPasswordHistoryCount())
                .anyMatch(entry -> passwordEncoder.matches(raw, entry.getPasswordHash()));
    }

    private void recordHistory(OrganisationUser user, String hash) {
        OrganisationPasswordHistory entry = new OrganisationPasswordHistory();
        entry.setUser(user);
        entry.setPasswordHash(hash);
        historyRepository.save(entry);
    }

    private void trimHistory(Long userId, int keep) {
        List<OrganisationPasswordHistory> recent = historyRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (recent.size() <= keep) {
            return;
        }
        List<Long> keepIds = recent.stream().limit(keep).map(OrganisationPasswordHistory::getId).toList();
        historyRepository.deleteByUserIdAndIdNotIn(userId, keepIds);
    }

    private void validateConfig(OrganisationAuthConfig config) {
        if (config.getPasswordMinLength() > config.getPasswordMaxLength()) {
            throw new BadRequestException("Password minimum length cannot exceed maximum length");
        }
    }

    private OrganisationAuthConfigResponse toResponse(OrganisationAuthConfig config) {
        return new OrganisationAuthConfigResponse(
                config.getAuthType(),
                config.getPasswordMinLength(),
                config.getPasswordMaxLength(),
                config.getPasswordExpirationDays(),
                config.getPasswordHistoryCount());
    }
}
