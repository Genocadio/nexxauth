package com.nexxserve.nauth.mapper;

import com.nexxserve.nauth.dto.request.CreateOrganisationRoleRequest;
import com.nexxserve.nauth.dto.response.OrganisationRoleResponse;
import com.nexxserve.nauth.entity.OrganisationRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface OrganisationRoleMapper {

    @Mapping(target = "isDefault", source = "defaultRole")
    OrganisationRoleResponse toResponse(OrganisationRole role);

    /** {@code isDefault} is applied by the service (null -> keep false). */
    @Mapping(target = "organisation", ignore = true)
    @Mapping(target = "defaultRole", ignore = true)
    OrganisationRole toEntity(CreateOrganisationRoleRequest request);
}
