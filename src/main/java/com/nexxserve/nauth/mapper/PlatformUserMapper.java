package com.nexxserve.nauth.mapper;

import com.nexxserve.nauth.dto.request.AddPlatformUserRequest;
import com.nexxserve.nauth.dto.request.RegisterRequest;
import com.nexxserve.nauth.dto.response.PlatformSummary;
import com.nexxserve.nauth.dto.response.PlatformUserResponse;
import com.nexxserve.nauth.entity.Platform;
import com.nexxserve.nauth.entity.PlatformUser;
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
