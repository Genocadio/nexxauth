package com.nexxserve.nexxauth.mapper;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationUserRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationUserResponse;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper(uses = OrganisationRoleMapper.class)
public interface OrganisationUserMapper {

    OrganisationUserResponse toResponse(OrganisationUser user, Map<String, String> metadata);

    @Mapping(target = "organisation", ignore = true)
    @Mapping(target = "roles", ignore = true)
    OrganisationUser toEntity(CreateOrganisationUserRequest request);
}
