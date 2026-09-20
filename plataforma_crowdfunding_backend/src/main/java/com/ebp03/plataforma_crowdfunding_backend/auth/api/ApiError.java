package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import java.util.List;

public record ApiError(String code, String message, List<String> details) {
    public ApiError(String code, String message) { this(code, message, List.of()); }
}
