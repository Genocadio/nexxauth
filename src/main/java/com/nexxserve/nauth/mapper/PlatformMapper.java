package com.nexxserve.nauth.mapper;

import com.nexxserve.nauth.dto.response.PlatformResponse;
import com.nexxserve.nauth.entity.Platform;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface PlatformMapper {

    @Mapping(target = "userCount", source = "userCount")
    @Mapping(target = "apiBaseUrl", source = "apiBaseUrl")
    PlatformResponse toResponse(Platform platform, long userCount, String apiBaseUrl);
}
