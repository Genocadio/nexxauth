package com.nexxserve.nexxauth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int length = value.length();
        if (length < 8 || length > 72) {
            return false;
        }
        // BCrypt hashes at most 72 bytes; silently truncating would make
        // distinct long passwords equivalent.
        return value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
