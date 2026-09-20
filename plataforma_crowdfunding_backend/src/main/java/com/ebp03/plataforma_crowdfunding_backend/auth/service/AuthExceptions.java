package com.ebp03.plataforma_crowdfunding_backend.auth.service;

import java.util.List;

public final class AuthExceptions {
    private AuthExceptions() { }

    public static class EmailAlreadyInUseException extends RuntimeException { }
    public static class InvalidCredentialsException extends RuntimeException { }
    public static class InvalidOAuthCodeException extends RuntimeException { }
    public static class InvalidEmailException extends RuntimeException { }
    public static class InvalidRoleException extends RuntimeException { }
    public static class WeakPasswordException extends RuntimeException {
        private final List<String> requirements;
        public WeakPasswordException(List<String> requirements) { this.requirements = requirements; }
        public List<String> getRequirements() { return requirements; }
    }
}
