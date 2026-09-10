package com.nexxserve.nexxauth.entity;

/**
 * Delivery channel of a verification code or link. Maps one-to-one to the
 * receiver address nexxauth sends to nexxbotify (email address vs phone
 * number) and to the flow id in the notification service.
 */
public enum VerificationChannel {
    EMAIL,
    SMS
}