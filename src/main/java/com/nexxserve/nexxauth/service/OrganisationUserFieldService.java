package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationUserFieldRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOrganisationUserFieldRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationUserFieldResponse;
import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import com.nexxserve.nexxauth.entity.OrganisationUserField;
import com.nexxserve.nexxauth.entity.OrganisationUserFieldValue;
import com.nexxserve.nexxauth.entity.Permission;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.entity.UserFieldType;
import com.nexxserve.nexxauth.exception.BadRequestException;
import com.nexxserve.nexxauth.exception.ConflictException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.repository.OrganisationUserFieldRepository;
import com.nexxserve.nexxauth.repository.OrganisationUserFieldValueRepository;
import com.nexxserve.nexxauth.repository.OrganisationUserRepository;
import com.nexxserve.nexxauth.security.OrgActor;
import com.nexxserve.nexxauth.util.Emails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Organisation-defined user fields. This service owns both the field
 * definitions (RBAC-gated CRUD) and the per-user values returned as
 * {@code metadata}, plus the login-by-field fallback used when the identifier
 * is neither a username nor an email. Values are canonical strings per
 * {@link UserFieldType}; login-enabled fields must keep values unique per org.
 */
@Service
public class OrganisationUserFieldService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final OrganisationUserFieldRepository fieldRepository;
    private final OrganisationUserFieldValueRepository valueRepository;
    private final OrganisationUserRepository userRepository;
    private final PlatformAccess platformAccess;
    private final OrganisationAccess organisationAccess;
    private final AuthAuditService audit;
    private final EntityManager entityManager;

    public OrganisationUserFieldService(OrganisationUserFieldRepository fieldRepository,
                                        OrganisationUserFieldValueRepository valueRepository,
                                        OrganisationUserRepository userRepository,
                                        PlatformAccess platformAccess,
                                        OrganisationAccess organisationAccess,
                                        AuthAuditService audit,
                                        EntityManager entityManager) {
        this.fieldRepository = fieldRepository;
        this.valueRepository = valueRepository;
        this.userRepository = userRepository;
        this.platformAccess = platformAccess;
        this.organisationAccess = organisationAccess;
        this.audit = audit;
        this.entityManager = entityManager;
    }

    // ------------------------------------------------------------------
    // Field definitions (config)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrganisationUserFieldResponse> listFields(String platformSlug, Long organisationId,
                                                          OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, false,
                Permission.ORGANISATION_USER_FIELD_READ);
        return fieldRepository.findByOrganisationIdOrderByKeyAsc(organisation.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OrganisationUserFieldResponse createField(String platformSlug, Long organisationId,
                                                     OrgActor requester, CreateOrganisationUserFieldRequest request) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, true,
                Permission.ORGANISATION_USER_FIELD_CREATE);
        lockOrganisation(organisation);
        String key = request.key().trim();
        if (fieldRepository.existsByOrganisationIdAndKey(organisation.getId(), key)) {
            throw new ConflictException("A user field with key " + key + " already exists in this organisation");
        }
        OrganisationUserField field = new OrganisationUserField();
        field.setOrganisation(organisation);
        field.setKey(key);
        field.setFieldType(request.fieldType());
        field.setLoginEnabled(Boolean.TRUE.equals(request.loginEnabled()));
        field.setRequired(Boolean.TRUE.equals(request.required()));
        OrganisationUserFieldResponse response = toResponse(fieldRepository.save(field));
        audit.logPersisted(LogLevel.INFO, LogCategory.CONFIG, AuthAuditService.ORG_USER_FIELD_CREATED, null, organisation.getSlug(), organisation.getId(), key);
        return response;
    }

    @Transactional
    public OrganisationUserFieldResponse updateField(String platformSlug, Long organisationId, Long fieldId,
                                                     OrgActor requester, UpdateOrganisationUserFieldRequest request) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, true,
                Permission.ORGANISATION_USER_FIELD_UPDATE);
        lockOrganisation(organisation);
        OrganisationUserField field = fieldRepository.findByIdAndOrganisationId(fieldId, organisation.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation user field", fieldId));
        if (request.fieldType() != null && request.fieldType() != field.getFieldType()) {
            if (valueRepository.existsByOrganisationIdAndFieldKey(organisation.getId(), field.getKey())) {
                throw new BadRequestException("Cannot change the type of field \"" + field.getKey()
                        + "\" while it has values; clear them first");
            }
            field.setFieldType(request.fieldType());
        }
        if (request.loginEnabled() != null && request.loginEnabled() != field.isLoginEnabled()) {
            if (request.loginEnabled()) {
                assertExistingValuesUnique(organisation, field);
            }
            field.setLoginEnabled(request.loginEnabled());
        }
        if (request.required() != null) {
            field.setRequired(request.required());
        }
        OrganisationUserFieldResponse response = toResponse(fieldRepository.save(field));
        audit.logPersisted(LogLevel.INFO, LogCategory.CONFIG, AuthAuditService.ORG_USER_FIELD_UPDATED, null, organisation.getSlug(), organisation.getId(), field.getKey());
        return response;
    }

    @Transactional
    public void deleteField(String platformSlug, Long organisationId, Long fieldId, OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, true,
                Permission.ORGANISATION_USER_FIELD_DELETE);
        lockOrganisation(organisation);
        OrganisationUserField field = fieldRepository.findByIdAndOrganisationId(fieldId, organisation.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation user field", fieldId));
        valueRepository.deleteByOrganisationIdAndFieldKey(organisation.getId(), field.getKey());
        fieldRepository.delete(field);
        audit.logPersisted(LogLevel.INFO, LogCategory.CONFIG, AuthAuditService.ORG_USER_FIELD_DELETED, null, organisation.getSlug(), organisation.getId(), field.getKey());
    }

    // ------------------------------------------------------------------
    // Per-user values (metadata)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, String> readMetadata(Long userId) {
        return valueRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(OrganisationUserFieldValue::getFieldKey,
                        OrganisationUserFieldValue::getFieldValue));
    }

    /** Batch metadata read for a list of users (avoids N+1 on user lists). */
    @Transactional(readOnly = true)
    public Map<Long, Map<String, String>> readMetadataByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, String>> byUser = new HashMap<>();
        for (OrganisationUserFieldValue value : valueRepository.findByUserIdIn(userIds)) {
            byUser.computeIfAbsent(value.getUserId(), id -> new HashMap<>())
                    .put(value.getFieldKey(), value.getFieldValue());
        }
        return byUser;
    }

    /**
     * Applies partial metadata changes to a (saved) user. Only the keys present
     * are touched; a null or blank value removes the key. Keys must be defined
     * fields and values are validated/normalized per field type; login-enabled
     * fields must stay unique per organisation. Must run inside the caller's
     * transaction (it persists value rows).
     */
    @Transactional
    public void setMetadata(OrganisationUser user, Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        Organisation organisation = user.getOrganisation();
        // Serializes value writes per organisation so the login-enabled
        // uniqueness checks cannot race with concurrent writes (the field
        // value column is not DB-unique).
        lockOrganisation(organisation);
        Map<String, OrganisationUserField> configured = fieldRepository
                .findByOrganisationId(organisation.getId()).stream()
                .collect(Collectors.toMap(OrganisationUserField::getKey, Function.identity()));
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String fieldKey = entry.getKey();
            OrganisationUserField field = configured.get(fieldKey);
            if (field == null) {
                throw new BadRequestException("Unknown user field: " + fieldKey);
            }
            String normalized = entry.getValue() == null ? null : normalizeForStore(field.getFieldType(), entry.getValue());
            if (normalized == null) {
                valueRepository.findByUserIdAndFieldKey(user.getId(), fieldKey)
                        .ifPresent(valueRepository::delete);
                continue;
            }
            if (field.isLoginEnabled()) {
                assertValueUnique(organisation, field, normalized, user.getId());
            }
            OrganisationUserFieldValue row = valueRepository.findByUserIdAndFieldKey(user.getId(), fieldKey)
                    .orElseGet(() -> {
                        OrganisationUserFieldValue created = new OrganisationUserFieldValue();
                        created.setUserId(user.getId());
                        created.setOrganisationId(organisation.getId());
                        created.setFieldKey(fieldKey);
                        return created;
                    });
            row.setFieldValue(normalized);
            valueRepository.save(row);
        }
    }

    // ------------------------------------------------------------------
    // Login by a login-enabled field
    // ------------------------------------------------------------------

    /**
     * Tries every login-enabled field of the organisation (in key order) with
     * the given identifier, normalized per each field's type. Returns the
     * matching user with roles eagerly loaded, or empty when no field matches.
     */
    @Transactional(readOnly = true)
    public Optional<OrganisationUser> findUserByLoginField(Organisation organisation, String identifier) {
        for (OrganisationUserField field
                : fieldRepository.findByOrganisationIdAndLoginEnabledTrueOrderByKeyAsc(organisation.getId())) {
            String normalized = normalizeForLogin(field.getFieldType(), identifier);
            if (normalized == null) {
                continue;
            }
            List<Long> matches = field.getFieldType() == UserFieldType.STRING
                    ? valueRepository.findUserIdsByOrganisationIdAndFieldKeyAndFieldValueIgnoreCase(
                            organisation.getId(), field.getKey(), normalized)
                    : valueRepository.findUserIdsByOrganisationIdAndFieldKeyAndFieldValue(
                            organisation.getId(), field.getKey(), normalized);
            Optional<Long> userId = matches.stream().findFirst();
            if (userId.isPresent()) {
                return userRepository.findWithRolesById(userId.get());
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void assertExistingValuesUnique(Organisation organisation, OrganisationUserField field) {
        List<OrganisationUserFieldValue> values =
                valueRepository.findByOrganisationIdAndFieldKey(organisation.getId(), field.getKey());
        Map<String, Long> distinctUsers = new HashMap<>();
        for (OrganisationUserFieldValue value : values) {
            // STRING values are case-insensitive identifiers, so "ABC" and
            // "abc" are the same login value.
            String dedupKey = field.getFieldType() == UserFieldType.STRING
                    ? value.getFieldValue().toLowerCase(Locale.ROOT)
                    : value.getFieldValue();
            Long previous = distinctUsers.putIfAbsent(dedupKey, value.getUserId());
            if (previous != null && !previous.equals(value.getUserId())) {
                throw new ConflictException("Cannot enable login on field \"" + field.getKey()
                        + "\": the value \"" + value.getFieldValue() + "\" is used by more than one user");
            }
        }
    }

    private void assertValueUnique(Organisation organisation, OrganisationUserField field, String value, Long userId) {
        Long exclude = userId == null ? -1L : userId;
        boolean exists = field.getFieldType() == UserFieldType.STRING
                ? valueRepository.existsByOrganisationIdAndFieldKeyAndFieldValueIgnoreCaseAndUserIdNot(
                        organisation.getId(), field.getKey(), value, exclude)
                : valueRepository.existsByOrganisationIdAndFieldKeyAndFieldValueAndUserIdNot(
                        organisation.getId(), field.getKey(), value, exclude);
        if (exists) {
            throw new ConflictException("Another user already has the value \"" + value
                    + "\" for login field " + field.getKey());
        }
    }

    /** Serializes field-config and value writes per organisation row, matching
     * the other org services (key rotation, auth config, session settings), so
     * the login-enabled uniqueness checks are race-free. */
    private void lockOrganisation(Organisation organisation) {
        entityManager.lock(entityManager.merge(organisation), LockModeType.PESSIMISTIC_WRITE);
    }

    /** Canonical form of a value for storage; throws on type mismatches. */
    private String normalizeForStore(UserFieldType type, String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String normalized = switch (type) {
            case STRING -> trimmed;
            case NUMBER -> {
                try {
                    yield new BigDecimal(trimmed).stripTrailingZeros().toPlainString();
                } catch (NumberFormatException e) {
                    throw new BadRequestException("Value must be a number for a NUMBER field");
                }
            }
            case BOOLEAN -> {
                if (trimmed.equalsIgnoreCase("true")) {
                    yield "true";
                } else if (trimmed.equalsIgnoreCase("false")) {
                    yield "false";
                } else {
                    throw new BadRequestException("Value must be true or false for a BOOLEAN field");
                }
            }
            case DATE -> {
                try {
                    yield LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE).toString();
                } catch (DateTimeParseException e) {
                    throw new BadRequestException("Value must be a date (yyyy-MM-dd) for a DATE field");
                }
            }
            case EMAIL -> {
                String emailValue = Emails.normalize(trimmed);
                if (emailValue == null || !EMAIL_PATTERN.matcher(emailValue).matches()) {
                    throw new BadRequestException("Value must be a valid email for an EMAIL field");
                }
                yield emailValue;
            }
            case LINK -> {
                try {
                    URI uri = URI.create(trimmed);
                    String scheme = uri.getScheme();
                    if (scheme == null || uri.getHost() == null
                            || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                        throw new BadRequestException("Value must be an http(s) link for a LINK field");
                    }
                    yield trimmed;
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Value must be an http(s) link for a LINK field");
                }
            }
        };
        if (normalized.length() > 255) {
            throw new BadRequestException("Metadata value is too long (max 255 characters)");
        }
        return normalized;
    }

    /** Like {@link #normalizeForStore} but lenient: unparseable values simply
     * don't match a login field instead of failing the login request. */
    private String normalizeForLogin(UserFieldType type, String raw) {
        try {
            return normalizeForStore(type, raw);
        } catch (BadRequestException e) {
            return null;
        }
    }

    private OrganisationUserFieldResponse toResponse(OrganisationUserField field) {
        return new OrganisationUserFieldResponse(field.getId(), field.getKey(),
                field.getFieldType(), field.isLoginEnabled(), field.isRequired(), field.getCreatedAt());
    }

    private Organisation resolve(String platformSlug, Long organisationId, OrgActor requester, boolean write) {
        return resolve(platformSlug, organisationId, requester, write, null);
    }

    private Organisation resolve(String platformSlug, Long organisationId, OrgActor requester,
                                 boolean write, Permission permission) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisationById(organisationId);
        if (write) {
            if (permission == null) {
                platformAccess.requireSuperUser(platform, requester);
            } else {
                organisationAccess.requireWrite(platform, organisation, requester, permission);
            }
        } else {
            if (permission == null) {
                organisationAccess.requireRead(platform, organisation, requester);
            } else {
                organisationAccess.requireRead(platform, organisation, requester, permission);
            }
        }
        return organisation;
    }
}
