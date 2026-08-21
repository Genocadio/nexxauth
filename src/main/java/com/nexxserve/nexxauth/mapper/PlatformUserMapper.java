package com.nexxserve.nexxauth.mapper;

import com.nexxserve.nexxauth.dto.request.AddPlatformUserRequest;
import com.nexxserve.nexxauth.dto.request.RegisterRequest;
import com.nexxserve.nexxauth.dto.response.PlatformSummary;
import com.nexxserve.nexxauth.dto.response.PlatformUserResponse;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.entity.PlatformUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface PlatformUserMapper {

    PlatformUserResponse toResponse(PlatformUser user);

    PlatformSummary toSummary(Platform platform);

    @Mapping(target = "password", ignore = true)
    PlatformUser toEntity(RegisterRequest request);

    @Mapping(target = "password", ignore = true)
    PlatformUser toEntity(AddPlatformUserRequest request);
}
