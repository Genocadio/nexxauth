package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.entity.OrgUserAction;
import com.nexxserve.nexxauth.entity.OrganisationAuthConfig;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import com.nexxserve.nexxauth.entity.OrganisationUserField;
import com.nexxserve.nexxauth.entity.OrganisationUserFieldValue;
import com.nexxserve.nexxauth.repository.OrganisationUserFieldRepository;
import com.nexxserve.nexxauth.repository.OrganisationUserFieldValueRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes the pending {@link OrgUserAction actions} of an organisation user,
 * returned on every org login/refresh. Actions are additive - one user can have
 * several at once - and the list is empty for a fully onboarded user. Gating
 * actions (today: {@code CHANGE_PASSWORD}) additionally restrict the session:
 * fixed 5-minute access token, no refresh token, and only the action endpoints
 * reachable until resolved.
 */
@Component
public class OrgUserActions {

    /** Access-token lifetime issued while a gating action is pending, fixed
     * regardless of the organisation's session settings. */
    public static final Duration GATING_ACCESS_TTL = Duration.ofMinutes(5);

    private final OrganisationUserFieldRepository fieldRepository;
    private final OrganisationUserFieldValueRepository valueRepository;
    private final OrganisationAuthConfigService authConfigService;

    public OrgUserActions(OrganisationUserFieldRepository fieldRepository,
                          OrganisationUserFieldValueRepository valueRepository,
                          OrganisationAuthConfigService authConfigService) {
        this.fieldRepository = fieldRepository;
        this.valueRepository = valueRepository;
        this.authConfigService = authConfigService;
    }

    /** All actions the user must resolve, in a stable order. */
    public List<OrgUserAction> of(OrganisationUser user) {
        List<OrgUserAction> actions = new ArrayList<>();
        if (user.isTemporaryPassword()) {
            actions.add(OrgUserAction.CHANGE_PASSWORD);
        }
        if (hasMissingRequiredFields(user)) {
            actions.add(OrgUserAction.UPDATE_PROFILE);
        }
        addVerificationActions(user, actions);
        return actions;
    }

    /** True when a gating action is pending: the login issues a short-lived
     * access token without a refresh token and all endpoints but the action
     * endpoints stay closed. CHANGE_PASSWORD, plus a register-required
     * verification (VERIFY_EMAIL / VERIFY_PHONE) when the org demands it on
     * registration, are the gating actions today. */
    public boolean hasPendingGatingAction(OrganisationUser user) {
        if (user.isTemporaryPassword()
                || (user.isRequireEmailVerificationAtNextLogin() && user.getPrimaryEmail() != null && user.getEmailVerifiedAt() == null)
                || (user.isRequirePhoneVerificationAtNextLogin() && user.getPrimaryPhone() != null && user.getPhoneVerifiedAt() == null)) {
            return true;
        }
        OrganisationAuthConfig config = authConfigService.configOf(user.getOrganisation());
        boolean unverifiedRequiredEmail = config.isRequireEmailVerificationOnRegister()
                && user.getPrimaryEmail() != null && user.getEmailVerifiedAt() == null;
        boolean unverifiedRequiredPhone = config.isRequirePhoneVerificationOnRegister()
                && user.getPrimaryPhone() != null && user.getPhoneVerifiedAt() == null;
        return unverifiedRequiredEmail || unverifiedRequiredPhone;
    }

    /** True when at least one required organisation user field has no value. */
    private boolean hasMissingRequiredFields(OrganisationUser user) {
        List<OrganisationUserField> required =
                fieldRepository.findByOrganisationIdAndRequiredTrue(user.getOrganisation().getId());
        if (required.isEmpty()) {
            return false;
        }
        Map<String, String> values = valueRepository.findByUserId(user.getId()).stream()
                .collect(Collectors.toMap(OrganisationUserFieldValue::getFieldKey,
                        OrganisationUserFieldValue::getFieldValue));
        return required.stream().anyMatch(field -> !values.containsKey(field.getKey()));
    }

    /** Surfaces VERIFY_EMAIL / VERIFY_PHONE for users whose organisation
     * requires the respective verification (either the standing enabled
     * flow or the register-time requirement or forced at next login) and the address is
     * unverified. */
    private void addVerificationActions(OrganisationUser user, List<OrgUserAction> actions) {
        OrganisationAuthConfig config = authConfigService.configOf(user.getOrganisation());
        boolean emailRequired = config.isEmailVerificationEnabled()
                || config.isRequireEmailVerificationOnRegister()
                || user.isRequireEmailVerificationAtNextLogin();
        if (emailRequired && user.getPrimaryEmail() != null && user.getEmailVerifiedAt() == null) {
            actions.add(OrgUserAction.VERIFY_EMAIL);
        }
        boolean phoneRequired = config.isPhoneVerificationEnabled()
                || config.isRequirePhoneVerificationOnRegister()
                || user.isRequirePhoneVerificationAtNextLogin();
        if (phoneRequired && user.getPrimaryPhone() != null && user.getPhoneVerifiedAt() == null) {
            actions.add(OrgUserAction.VERIFY_PHONE);
        }
    }
}
