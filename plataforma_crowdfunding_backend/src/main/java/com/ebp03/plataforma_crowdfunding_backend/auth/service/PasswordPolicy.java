package com.ebp03.plataforma_crowdfunding_backend.auth.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
    public List<String> missingRequirements(String password) {
        List<String> missing = new ArrayList<>();
        if (password == null || password.length() < 8) missing.add("MINIMUM_LENGTH_8");
        if (password == null || !password.chars().anyMatch(Character::isUpperCase)) missing.add("ONE_UPPERCASE_LETTER");
        if (password == null || !password.chars().anyMatch(Character::isDigit)) missing.add("ONE_NUMBER");
        if (password == null || password.chars().allMatch(Character::isLetterOrDigit)) missing.add("ONE_SYMBOL");
        return missing;
    }
}
