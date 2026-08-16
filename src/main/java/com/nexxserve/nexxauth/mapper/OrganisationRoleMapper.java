package com.nexxserve.nexxauth.mapper;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationRoleRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationRoleResponse;
import com.nexxserve.nexxauth.dto.response.OrganisationUserRoleResponse;
import com.nexxserve.nexxauth.entity.OrganisationRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface OrganisationRoleMapper {

    @Mapping(target = "isDefault", source = "defaultRole")
    OrganisationRoleResponse toResponse(OrganisationRole role);

    /** The minimal role shape used inside user responses (id + name only). */
    OrganisationUserRoleResponse toUserRoleResponse(OrganisationRole role);

    /** {@code isDefault} is applied by the service (null -> keep false). */
    @Mapping(target = "organisation", ignore = true)
    @Mapping(target = "defaultRole", ignore = true)
    OrganisationRole toEntity(CreateOrganisationRoleRequest request);
}
