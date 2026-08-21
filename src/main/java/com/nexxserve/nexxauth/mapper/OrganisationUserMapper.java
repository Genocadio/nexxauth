package com.nexxserve.nexxauth.mapper;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationUserRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationUserResponse;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper
public interface OrganisationUserMapper {

    @Mapping(target = "authTypes", expression = "java(user.getAuthType() == null ? java.util.List.of() : java.util.List.of(user.getAuthType()))")
    @Mapping(target = "roles", expression = "java(user.getRoles().stream().map(com.nexxserve.nexxauth.entity.OrganisationRole::getName).sorted().toList())")
    OrganisationUserResponse toResponse(OrganisationUser user, Map<String, String> metadata);

    @Mapping(target = "organisation", ignore = true)
    @Mapping(target = "roles", ignore = true)
    OrganisationUser toEntity(CreateOrganisationUserRequest request);
}
