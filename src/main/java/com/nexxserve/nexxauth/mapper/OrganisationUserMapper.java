package com.nexxserve.nexxauth.mapper;

import com.nexxserve.nexxauth.dto.request.CreateOrganisationUserRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationUserEmailResponse;
import com.nexxserve.nexxauth.dto.response.OrganisationUserPhoneResponse;
import com.nexxserve.nexxauth.dto.response.OrganisationUserResponse;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import com.nexxserve.nexxauth.entity.OrganisationUserEmail;
import com.nexxserve.nexxauth.entity.OrganisationUserPhone;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrganisationUserMapper {

    @Mapping(target = "email", expression = "java(user.getPrimaryEmail())")
    @Mapping(target = "phone", expression = "java(user.getPrimaryPhone())")
    @Mapping(target = "emailVerified", expression = "java(user.isEmailVerified())")
    @Mapping(target = "phoneVerified", expression = "java(user.isPhoneVerified())")
    @Mapping(target = "emails", expression = "java(mapEmails(user))")
    @Mapping(target = "phones", expression = "java(mapPhones(user))")
    @Mapping(target = "authTypes", expression = "java(user.getAuthType() == null ? List.of() : List.of(user.getAuthType()))")
    @Mapping(target = "roles", expression = "java(user.getRoles().stream().map(com.nexxserve.nexxauth.entity.OrganisationRole::getName).sorted().toList())")
    OrganisationUserResponse toResponse(OrganisationUser user, Map<String, String> metadata);

    default List<OrganisationUserEmailResponse> mapEmails(OrganisationUser user) {
        if (user.getEmails() == null) return List.of();
        return user.getEmails().stream()
                .sorted(Comparator.comparing(OrganisationUserEmail::isPrimary).reversed()
                        .thenComparing(OrganisationUserEmail::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toEmailResponse)
                .toList();
    }

    default List<OrganisationUserPhoneResponse> mapPhones(OrganisationUser user) {
        if (user.getPhones() == null) return List.of();
        return user.getPhones().stream()
                .sorted(Comparator.comparing(OrganisationUserPhone::isPrimary).reversed()
                        .thenComparing(OrganisationUserPhone::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toPhoneResponse)
                .toList();
    }

    @Mapping(target = "isPrimary", expression = "java(email.isPrimary())")
    @Mapping(target = "verified", expression = "java(email.isVerified())")
    OrganisationUserEmailResponse toEmailResponse(OrganisationUserEmail email);

    @Mapping(target = "isPrimary", expression = "java(phone.isPrimary())")
    @Mapping(target = "verified", expression = "java(phone.isVerified())")
    OrganisationUserPhoneResponse toPhoneResponse(OrganisationUserPhone phone);

    @Mapping(target = "organisation", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "emails", ignore = true)
    @Mapping(target = "phones", ignore = true)
    OrganisationUser toEntity(CreateOrganisationUserRequest request);
}
