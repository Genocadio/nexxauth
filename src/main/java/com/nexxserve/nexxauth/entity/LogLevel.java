package com.nexxserve.nexxauth.entity;

/**
 * Severity of a persisted log entry. Mirrors the standard logging levels
 * but is kept as an enum so it is a first-class column in the database
 * and can be filtered efficiently.
 */
public enum LogLevel {
    INFO,
    WARN,
    ERROR
}
