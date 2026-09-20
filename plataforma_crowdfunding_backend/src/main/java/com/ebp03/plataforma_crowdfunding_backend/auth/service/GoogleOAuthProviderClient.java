package com.ebp03.plataforma_crowdfunding_backend.auth.service;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GoogleOAuthProviderClient implements OAuthProviderClient {
    private final RestClient restClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;

    public GoogleOAuthProviderClient(
            @Value("${app.oauth.google.token-uri:}") String tokenUri,
            @Value("${app.oauth.google.client-id:}") String clientId,
            @Value("${app.oauth.google.client-secret:}") String clientSecret) {
        this.restClient = RestClient.builder().build();
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public OAuthIdentity exchangeCode(String provider, String authorizationCode) {
        if (!"google".equalsIgnoreCase(provider) || tokenUri.isBlank() || clientId.isBlank() || clientSecret.isBlank()) {
            throw new AuthExceptions.InvalidOAuthCodeException();
        }
        try {
            GoogleToken token = restClient.post().uri(tokenUri)
                    .body(Map.of("code", authorizationCode, "client_id", clientId, "client_secret", clientSecret, "grant_type", "authorization_code"))
                    .retrieve().body(GoogleToken.class);
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) throw new AuthExceptions.InvalidOAuthCodeException();
            GoogleProfile profile = restClient.get().uri("https://openidconnect.googleapis.com/v1/userinfo")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken()).retrieve().body(GoogleProfile.class);
            if (profile == null || profile.sub() == null || profile.email() == null || profile.email().isBlank()) throw new AuthExceptions.InvalidOAuthCodeException();
            return new OAuthIdentity(profile.sub(), profile.email(), profile.name() == null ? profile.email() : profile.name());
        } catch (RuntimeException exception) {
            throw new AuthExceptions.InvalidOAuthCodeException();
        }
    }

    private record GoogleToken(@JsonProperty("access_token") String accessToken) { }
    private record GoogleProfile(String sub, String email, String name) { }
}
