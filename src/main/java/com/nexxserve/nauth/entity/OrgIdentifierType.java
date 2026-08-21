package com.nexxserve.nauth.entity;

/**
 * The kind of identifier a client sends at login. Lets the client say
 * unambiguously what it is authenticating with (usernames, emails and phone
 * numbers are all possible login identifiers); future auth methods (OTP to
 * email/phone, passkey resident keys, ...) will rely on the same typing.
 * When omitted, the backend falls back to trying each enabled identifier.
 */
public enum OrgIdentifierType {
    USERNAME,
    EMAIL,
    PHONE
}
