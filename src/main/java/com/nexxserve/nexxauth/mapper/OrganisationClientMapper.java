package com.nexxserve.nexxauth.mapper;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationClientRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationClientResponse;
import com.nexxserve.nexxauth.entity.OrganisationClient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps clients between entity and DTO. {@code token} is generated on
 * create/rotate (shown once); {@code settings} is serialized JSON.
 */
@Mapper
public interface OrganisationClientMapper {

    @Mapping(target = "token", ignore = true)
    @Mapping(target = "settings", ignore = true)
    OrganisationClientResponse toResponse(OrganisationClient client);

    @Mapping(target = "organisation", ignore = true)
    @Mapping(target = "tokenHash", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "settings", ignore = true)
    OrganisationClient toEntity(CreateOrganisationClientRequest request);

    default Set<String> splitRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(roles.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    default String joinRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        return String.join(",", roles);
    }
}
