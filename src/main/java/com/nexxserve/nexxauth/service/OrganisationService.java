package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOrganisationRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationResponse;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.exception.ConflictException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.mapper.OrganisationMapper;
import com.nexxserve.nexxauth.repository.OrganisationRepository;
import com.nexxserve.nexxauth.security.OrgActor;
import com.nexxserve.nexxauth.util.Slugs;
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
    public OrganisationResponse get(String platformSlug, String organisationSlug, OrgActor requester) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisation(platform, organisationSlug);
        if (requester.isPlatformUser()) {
            // Platform members read any org of their platform.
            platformAccess.requireMember(platform, requester);
        } else {
            // Org users can always read their own organisation (self-context,
            // like /users/me); the user directory is what needs the permission.
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
    public OrganisationResponse update(String platformSlug, String organisationSlug, OrgActor requester,
                                       UpdateOrganisationRequest request) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        platformAccess.requireSuperUser(platform, requester);
        Organisation organisation = organisationAccess.findOrganisation(platform, organisationSlug);

        if (request.name() != null) {
            organisation.setName(request.name());
        }
        if (request.description() != null) {
            organisation.setDescription(request.description());
        }
        if (request.useEmailAsUsername() != null) {
            organisation.setUseEmailAsUsername(request.useEmailAsUsername());
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

    @Transactional
    public void delete(String platformSlug, String organisationSlug, OrgActor requester) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        platformAccess.requireSuperUser(platform, requester);
        organisationRepository.delete(organisationAccess.findOrganisation(platform, organisationSlug));
    }
}
