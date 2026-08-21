package com.nexxserve.nauth.service;

import com.nexxserve.nauth.dto.request.CreateOrganisationRequest;
import com.nexxserve.nauth.dto.request.UpdateOrganisationRequest;
import com.nexxserve.nauth.dto.response.OrganisationResponse;
import com.nexxserve.nauth.entity.Organisation;
import com.nexxserve.nauth.entity.Platform;
import com.nexxserve.nauth.exception.BadRequestException;
import com.nexxserve.nauth.exception.ConflictException;
import com.nexxserve.nauth.exception.ResourceNotFoundException;
import com.nexxserve.nauth.mapper.OrganisationMapper;
import com.nexxserve.nauth.repository.OrganisationRepository;
import com.nexxserve.nauth.security.OrgActor;
import com.nexxserve.nauth.util.Slugs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Organisations live under an immutable platform slug. Reads accept both
 * platform members and org users (with the read permission); writes require
 * the platform super user role. Creating an organisation generates its own RSA
 * signing keys used to sign that organisation's access tokens.
 */
@Service
public class OrganisationService {

    private final OrganisationRepository organisationRepository;
    private final PlatformAccess platformAccess;
    private final OrganisationAccess organisationAccess;
    private final OrganisationMapper organisationMapper;
    private final OrgKeyService orgKeyService;

    public OrganisationService(OrganisationRepository organisationRepository, PlatformAccess platformAccess,
                               OrganisationAccess organisationAccess, OrganisationMapper organisationMapper,
                               OrgKeyService orgKeyService) {
        this.organisationRepository = organisationRepository;
        this.platformAccess = platformAccess;
        this.organisationAccess = organisationAccess;
        this.organisationMapper = organisationMapper;
        this.orgKeyService = orgKeyService;
    }

    @Transactional(readOnly = true)
    public List<OrganisationResponse> list(String platformSlug, OrgActor requester) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        platformAccess.requireMember(platform, requester);
        return organisationRepository.findByPlatformIdOrderByCreatedAtAsc(platform.getId()).stream()
                .map(organisationMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganisationResponse get(String platformSlug, Long organisationId, OrgActor requester) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisationById(organisationId);
        if (requester.isPlatformUser()) {
            platformAccess.requireMember(platform, requester);
        } else {
            organisationAccess.requireOrgUserOf(organisation, requester);
        }
        return organisationMapper.toResponse(organisation);
    }

    @Transactional
    public OrganisationResponse create(String platformSlug, OrgActor requester,
                                       CreateOrganisationRequest request) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        platformAccess.requireSuperUser(platform, requester);

        boolean explicitSlug = request.slug() != null && !request.slug().isBlank();
        String slug;
        if (explicitSlug) {
            // An explicitly requested slug is never silently renamed.
            if (organisationRepository.existsByPlatformIdAndSlug(platform.getId(), request.slug())) {
                throw new ConflictException("An organisation with slug " + request.slug()
                        + " already exists in this platform");
            }
            slug = request.slug();
        } else {
            slug = Slugs.uniqueSlug(Slugs.slugify(request.name()),
                    candidate -> organisationRepository.existsByPlatformIdAndSlug(platform.getId(), candidate));
        }

        Organisation organisation = organisationMapper.toEntity(request);
        organisation.setPlatform(platform);
        organisation.setSlug(slug);
        Organisation saved = organisationRepository.save(organisation);
        // Creating an organisation provisions the key pair that signs its tokens.
        orgKeyService.activeKey(saved);
        return organisationMapper.toResponse(saved);
    }

    @Transactional
    public OrganisationResponse update(String platformSlug, Long organisationId, OrgActor requester,
                                       UpdateOrganisationRequest request) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        platformAccess.requireSuperUser(platform, requester);
        Organisation organisation = organisationAccess.findOrganisationById(organisationId);

        if (request.name() != null) {
            organisation.setName(request.name());
        }
        if (request.description() != null) {
            organisation.setDescription(request.description());
        }
        // Legacy switch first, then explicit flags win over it.
        if (request.useEmailAsUsername() != null) {
            applyLegacyIdentifierSwitch(organisation, request.useEmailAsUsername());
        }
        if (request.emailRequired() != null) {
            organisation.setEmailRequired(request.emailRequired());
        }
        if (request.usernameRequired() != null) {
            organisation.setUsernameRequired(request.usernameRequired());
        }
        if (request.phoneRequired() != null) {
            organisation.setPhoneRequired(request.phoneRequired());
        }
        if (request.emailCanLogin() != null) {
            organisation.setEmailCanLogin(request.emailCanLogin());
        }
        if (request.usernameCanLogin() != null) {
            organisation.setUsernameCanLogin(request.usernameCanLogin());
        }
        if (request.phoneCanLogin() != null) {
            organisation.setPhoneCanLogin(request.phoneCanLogin());
        }
        if (request.onboardingStep() != null) {
            organisation.setOnboardingStep(request.onboardingStep());
        }
        if (!organisation.hasLoginIdentifier()) {
            throw new BadRequestException(
                    "At least one sign-in identifier (email, username or phone) must be enabled for login");
        }
        if (request.slug() != null && !request.slug().equals(organisation.getSlug())) {
            if (organisationRepository.existsByPlatformIdAndSlug(platform.getId(), request.slug())) {
                throw new ConflictException("An organisation with slug " + request.slug()
                        + " already exists in this platform");
            }
            organisation.setSlug(request.slug());
        }
        return organisationMapper.toResponse(organisationRepository.save(organisation));
    }

    /** Legacy {@code useEmailAsUsername} switch mapped onto the per-identifier
     * flags: email required + login identifier, username not login-capable and
     * not required. Phone flags are left untouched — the old switch predates
     * phone and must not silently disable a phone identifier configured via
     * the onboarding wizard. */
    private void applyLegacyIdentifierSwitch(Organisation organisation, boolean emailAsUsername) {
        organisation.setEmailRequired(emailAsUsername);
        organisation.setEmailCanLogin(emailAsUsername);
        organisation.setUsernameRequired(!emailAsUsername);
        organisation.setUsernameCanLogin(!emailAsUsername);
    }

    @Transactional
    public void delete(String platformSlug, Long organisationId, OrgActor requester) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        platformAccess.requireSuperUser(platform, requester);
        organisationRepository.delete(organisationAccess.findOrganisationById(organisationId));
    }
}
