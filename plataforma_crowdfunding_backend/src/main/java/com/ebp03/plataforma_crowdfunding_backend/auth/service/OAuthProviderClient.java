package com.ebp03.plataforma_crowdfunding_backend.auth.service;

public interface OAuthProviderClient {
    OAuthIdentity exchangeCode(String provider, String authorizationCode);

    record OAuthIdentity(String providerUserId, String email, String name) { }
}
