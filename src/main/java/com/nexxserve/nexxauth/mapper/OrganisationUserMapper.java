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
    @Mapping(target = "authTypes", expression = "java(mapAuthTypes(user))")
    @Mapping(target = "roles", expression = "java(user.getRoles().stream().map(com.nexxserve.nexxauth.entity.OrganisationRole::getName).sorted().toList())")
    OrganisationUserResponse toResponse(OrganisationUser user, Map<String, String> metadata);

    /**
     * What the user can sign in with right now. Derived rather than read from
     * {@code authType} so the list never advertises a credential that is not
     * usable: a password only counts when a hash is stored, and OTP only counts
     * when the user's method allows it and there is an address to deliver to.
     * A placeholder created without a password (method PASSWORD, no hash)
     * therefore reports nothing, which is what locks it out.
     */
    default List<com.nexxserve.nexxauth.entity.AuthType> mapAuthTypes(OrganisationUser user) {
        var method = user.getLoginMethod();
        if (method == null) return List.of();
        var types = new java.util.ArrayList<com.nexxserve.nexxauth.entity.AuthType>(2);
        if (method.allowsPassword() && user.getPasswordHash() != null) {
            types.add(com.nexxserve.nexxauth.entity.AuthType.PASSWORD);
        }
        if (method.allowsOtp() && (user.getPrimaryEmail() != null || user.getPrimaryPhone() != null)) {
            types.add(com.nexxserve.nexxauth.entity.AuthType.OTP);
        }
        return types;
    }

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
    // loginMethod is resolved by OrganisationUserService#create, which depends on
    // whether a password was supplied. Mapping the nullable request field here
    // would overwrite the entity's non-null default with null and violate the
    // NOT NULL column.
    @Mapping(target = "loginMethod", ignore = true)
    OrganisationUser toEntity(CreateOrganisationUserRequest request);
}
