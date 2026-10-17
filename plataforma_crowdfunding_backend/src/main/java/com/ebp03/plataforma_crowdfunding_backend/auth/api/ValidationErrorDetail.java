package com.ebp03.plataforma_crowdfunding_backend.auth.api;

public record ValidationErrorDetail(String field, String code, String message) {
}
