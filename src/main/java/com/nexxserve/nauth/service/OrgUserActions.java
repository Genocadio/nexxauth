package com.nexxserve.nauth.service;

import com.nexxserve.nauth.entity.OrgUserAction;
import com.nexxserve.nauth.entity.OrganisationUser;
import com.nexxserve.nauth.entity.OrganisationUserField;
import com.nexxserve.nauth.entity.OrganisationUserFieldValue;
import com.nexxserve.nauth.repository.OrganisationUserFieldRepository;
import com.nexxserve.nauth.repository.OrganisationUserFieldValueRepository;
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

    public OrgUserActions(OrganisationUserFieldRepository fieldRepository,
                          OrganisationUserFieldValueRepository valueRepository) {
        this.fieldRepository = fieldRepository;
        this.valueRepository = valueRepository;
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
        return actions;
    }

    /** True when a gating action is pending: the login issues a short-lived
     * access token without a refresh token and all endpoints but the action
     * endpoints stay closed. CHANGE_PASSWORD is the only gating action today. */
    public boolean hasPendingGatingAction(OrganisationUser user) {
        return user.isTemporaryPassword();
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
}
