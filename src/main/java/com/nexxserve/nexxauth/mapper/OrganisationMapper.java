package com.nexxserve.nexxauth.mapper;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationResponse;
import com.nexxserve.nexxauth.entity.Organisation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface OrganisationMapper {

    OrganisationResponse toResponse(Organisation organisation);

    @Mapping(target = "platform", ignore = true)
    @Mapping(target = "slug", ignore = true)
    Organisation toEntity(CreateOrganisationRequest request);
}
