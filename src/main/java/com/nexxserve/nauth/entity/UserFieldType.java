package com.nexxserve.nauth.entity;

/**
 * Value type of an organisation-defined user field. Values are stored as a
 * canonical string form:
 * <ul>
 *   <li>STRING: trimmed text (login identifiers match case-insensitively),</li>
 *   <li>NUMBER: {@code BigDecimal.toPlainString()} so {@code 1.50 == 1.5},</li>
 *   <li>BOOLEAN: {@code "true"}/{@code "false"},</li>
 *   <li>DATE: ISO-8601 {@code yyyy-MM-dd},</li>
 *   <li>EMAIL: trimmed + lowercased email (matches {@link com.nexxserve.nauth.util.Emails}),</li>
 *   <li>LINK: an http(s) URL ({@code "https://..."}).</li>
 * </ul>
 * Login-by-field normalizes the typed identifier the same way, so the stored
 * and the typed form must match exactly (case-insensitively for STRING).
 */
public enum UserFieldType {
    STRING,
    NUMBER,
    BOOLEAN,
    DATE,
    EMAIL,
    LINK
}
