package com.nexxserve.nexxauth.mapper;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationRoleRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationRoleResponse;
import com.nexxserve.nexxauth.entity.OrganisationRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface OrganisationRoleMapper {

    OrganisationRoleResponse toResponse(OrganisationRole role);

    @Mapping(target = "organisation", ignore = true)
    OrganisationRole toEntity(CreateOrganisationRoleRequest request);
}
