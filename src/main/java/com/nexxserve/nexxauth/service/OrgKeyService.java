package com.nexxserve.nexxauth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationSigningKey;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.repository.OrganisationSigningKeyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages the RSA key pairs an organisation uses to sign its own access tokens.
 * Keys are stored PEM-encoded; the active key signs, retired keys stay in the
 * table so tokens signed before a rotation still verify until they expire.
 * Parsed keys are cached per {@code kid} (keys are immutable once stored, and
 * rotation is additive), so token verification does not hit the database or
 * re-parse a PEM on every request.
 */
@Service
public class OrgKeyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OrganisationSigningKeyRepository keyRepository;
    private final AuthAuditService audit;
    private final EntityManager entityManager;

    private final Cache<String, RSAPublicKey> publicKeys = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(5_000)
            .build();

    private final Cache<String, RSAPrivateKey> privateKeys = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(5_000)
            .build();

    /** The key row per {@code kid}, so token verification never hits the
     * database. Retired keys stay valid (verification until expiry) and their
     * entries expire naturally; a rotated-in key is a new {@code kid} and is
     * loaded on first use. */
    private final Cache<String, OrganisationSigningKey> keysByKid = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(5_000)
            .build();

    /** Kids that are not in the table (forged {@code kid}s): cached misses bound
     * how often unknown kids can turn into a database lookup, so flooding org
     * endpoints with random kids cannot amplify into queries per request. */
    private final Cache<String, Boolean> unknownKids = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(1_000)
            .build();

    public OrgKeyService(OrganisationSigningKeyRepository keyRepository, AuthAuditService audit,
                         EntityManager entityManager) {
        this.keyRepository = keyRepository;
        this.audit = audit;
        this.entityManager = entityManager;
    }

    /** The organisation's active signing key, creating one lazily if missing
     * (e.g. organisations created before org auth existed). The lazy creation
     * is double-checked under a lock on the organisation row so concurrent
     * first logins cannot create two active keys. */
    @Transactional
    public OrganisationSigningKey activeKey(Organisation organisation) {
        return keyRepository.findByOrganisationIdAndActiveTrue(organisation.getId())
                .orElseGet(() -> createActiveKey(organisation));
    }

    private OrganisationSigningKey createActiveKey(Organisation organisation) {
        entityManager.lock(entityManager.merge(organisation), LockModeType.PESSIMISTIC_WRITE);
        return keyRepository.findByOrganisationIdAndActiveTrue(organisation.getId())
                .orElseGet(() -> save(generate(organisation)));
    }

    /** Rotates the signing key: the current one is retired, a fresh one becomes
     * active. The rotation runs under a lock on the organisation row (like lazy
     * creation) so two concurrent rotations cannot both insert an active key. */
    @Transactional
    public OrganisationSigningKey rotateKey(Organisation organisation) {
        entityManager.lock(entityManager.merge(organisation), LockModeType.PESSIMISTIC_WRITE);
        keyRepository.findByOrganisationIdAndActiveTrue(organisation.getId())
                .ifPresent(key -> {
                    key.setActive(false);
                    keyRepository.save(key);
                });
        OrganisationSigningKey key = save(generate(organisation));
        audit.logPersisted(LogLevel.INFO, LogCategory.SECURITY, AuthAuditService.ORG_KEY_ROTATED, null, organisation.getSlug(), organisation.getId(), null);
        return key;
    }

    @Transactional(readOnly = true)
    public List<OrganisationSigningKey> keys(Organisation organisation) {
        return keyRepository.findByOrganisationIdOrderByCreatedAtAsc(organisation.getId());
    }

    public OrganisationSigningKey findByKid(String kid) {
        if (unknownKids.getIfPresent(kid) != null) {
            throw ResourceNotFoundException.of("Organisation signing key", kid);
        }
        OrganisationSigningKey key = keysByKid.get(kid,
                unknown -> keyRepository.findByKid(unknown).orElse(null));
        if (key == null) {
            unknownKids.put(kid, Boolean.TRUE);
            throw ResourceNotFoundException.of("Organisation signing key", kid);
        }
        return key;
    }

    public RSAPublicKey publicKeyOf(OrganisationSigningKey key) {
        return publicKeys.get(key.getKid(), ignored -> parsePublicKey(key));
    }

    public RSAPrivateKey privateKeyOf(OrganisationSigningKey key) {
        return privateKeys.get(key.getKid(), ignored -> parsePrivateKey(key));
    }

    private RSAPublicKey parsePublicKey(OrganisationSigningKey key) {
        try {
            byte[] der = Base64.getDecoder().decode(key.getPublicKey());
            return (RSAPublicKey) java.security.KeyFactory.getInstance("RSA")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Stored organisation public key is corrupt", e);
        }
    }

    private RSAPrivateKey parsePrivateKey(OrganisationSigningKey key) {
        try {
            byte[] der = Base64.getDecoder().decode(key.getPrivateKey());
            return (RSAPrivateKey) java.security.KeyFactory.getInstance("RSA")
                    .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Stored organisation private key is corrupt", e);
        }
    }

    private OrganisationSigningKey generate(Organisation organisation) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, SECURE_RANDOM);
            KeyPair pair = generator.generateKeyPair();

            OrganisationSigningKey key = new OrganisationSigningKey();
            key.setOrganisation(organisation);
            key.setKid(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            key.setPublicKey(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            key.setPrivateKey(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            key.setActive(true);
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate organisation signing key", e);
        }
    }

    private OrganisationSigningKey save(OrganisationSigningKey key) {
        return keyRepository.save(key);
    }
}
