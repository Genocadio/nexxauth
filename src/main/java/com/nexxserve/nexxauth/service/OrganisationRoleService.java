package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationRoleRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOrganisationRoleRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationRoleResponse;
import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationRole;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.entity.Permission;
import com.nexxserve.nexxauth.exception.BadRequestException;
import com.nexxserve.nexxauth.exception.ConflictException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.mapper.OrganisationRoleMapper;
import com.nexxserve.nexxauth.repository.OrganisationRoleRepository;
import com.nexxserve.nexxauth.security.OrgActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Organisation roles: named groups of app-fixed permissions. A role may have
 * zero permissions. Roles are organisation-scoped and mean nothing at the
 * platform level.
 */
@Service
public class OrganisationRoleService {

    private final OrganisationRoleRepository roleRepository;
    private final PlatformAccess platformAccess;
    private final OrganisationAccess organisationAccess;
    private final OrganisationRoleMapper roleMapper;
    private final AuthAuditService audit;

    public OrganisationRoleService(OrganisationRoleRepository roleRepository, PlatformAccess platformAccess,
                                   OrganisationAccess organisationAccess, OrganisationRoleMapper roleMapper,
                                   AuthAuditService audit) {
        this.roleRepository = roleRepository;
        this.platformAccess = platformAccess;
        this.organisationAccess = organisationAccess;
        this.roleMapper = roleMapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OrganisationRoleResponse> list(String platformSlug, Long organisationId,
                                               OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, false);
        return roleRepository.findByOrganisationIdOrderByCreatedAtAsc(organisation.getId()).stream()
                .map(roleMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganisationRoleResponse get(String platformSlug, Long organisationId, Long roleId,
                                        OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, false);
        return roleMapper.toResponse(findRole(organisation, roleId));
    }

    @Transactional
    public OrganisationRoleResponse create(String platformSlug, Long organisationId, OrgActor requester,
                                           CreateOrganisationRoleRequest request) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, true);
        if (roleRepository.existsByOrganisationIdAndName(organisation.getId(), request.name())) {
            throw new ConflictException("A role named " + request.name()
                    + " already exists in this organisation");
        }
        OrganisationRole role = roleMapper.toEntity(request);
        role.setOrganisation(organisation);
        role.setDefaultRole(Boolean.TRUE.equals(request.isDefault()));
        rejectNullPermissions(request.permissions());
        OrganisationRole saved = roleRepository.save(role);
        audit.logPersisted(LogLevel.INFO, LogCategory.CONFIG, AuthAuditService.ORG_ROLE_CREATED, null,
                organisation.getSlug(), organisation.getId(), saved.getName());
        return roleMapper.toResponse(saved);
    }

    @Transactional
    public OrganisationRoleResponse update(String platformSlug, Long organisationId, Long roleId,
                                           OrgActor requester, UpdateOrganisationRoleRequest request) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, true);
        OrganisationRole role = findRole(organisation, roleId);

        if (request.name() != null && !request.name().equals(role.getName())) {
            if (roleRepository.existsByOrganisationIdAndName(organisation.getId(), request.name())) {
                throw new ConflictException("A role named " + request.name()
                        + " already exists in this organisation");
            }
            role.setName(request.name());
        }
        if (request.permissions() != null) {
            rejectNullPermissions(request.permissions());
            role.setPermissions(request.permissions());
        }
        if (request.isDefault() != null) {
            role.setDefaultRole(request.isDefault());
        }
        OrganisationRole saved = roleRepository.save(role);
        audit.logPersisted(LogLevel.INFO, LogCategory.CONFIG, AuthAuditService.ORG_ROLE_UPDATED, null,
                organisation.getSlug(), organisation.getId(), saved.getName());
        return roleMapper.toResponse(saved);
    }

    @Transactional
    public void delete(String platformSlug, Long organisationId, Long roleId, OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, true);
        OrganisationRole role = findRole(organisation, roleId);
        audit.logPersisted(LogLevel.INFO, LogCategory.CONFIG, AuthAuditService.ORG_ROLE_DELETED, null,
                organisation.getSlug(), organisation.getId(), role.getName());
        roleRepository.delete(role);
    }

    /** A null element in the permission set would fail the NOT NULL join-table
     * constraint with a confusing 409; reject it up front with a clear 400. */
    private void rejectNullPermissions(java.util.Set<Permission> permissions) {
        if (permissions != null && permissions.contains(null)) {
            throw new BadRequestException("Permissions may not contain null values");
        }
    }

    private OrganisationRole findRole(Organisation organisation, Long roleId) {
        return roleRepository.findByIdAndOrganisationId(roleId, organisation.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation role", roleId));
    }

    private Organisation resolve(String platformSlug, Long organisationId, OrgActor requester,
                                 boolean write) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisationById(organisationId);
        if (write) {
            platformAccess.requireSuperUser(platform, requester);
        } else {
            organisationAccess.requireRead(platform, organisation, requester);
        }
        return organisation;
    }
}
