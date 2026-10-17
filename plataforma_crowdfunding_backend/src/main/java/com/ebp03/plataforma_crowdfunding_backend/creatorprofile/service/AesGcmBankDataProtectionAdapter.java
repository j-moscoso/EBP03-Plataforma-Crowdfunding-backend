package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.service;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AesGcmBankDataProtectionAdapter implements BankDataProtectionPort {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmBankDataProtectionAdapter(
            @Value("${app.creator-profile.encryption-key:}") String encodedKey) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "CREATOR_PROFILE_ENCRYPTION_KEY must be base64 encoding of a randomly generated 32-byte key");
        }
        if (keyBytes.length != 32) {
            Arrays.fill(keyBytes, (byte) 0);
            throw new IllegalStateException(
                    "CREATOR_PROFILE_ENCRYPTION_KEY must be base64 encoding of a randomly generated 32-byte key");
        }
        key = new SecretKeySpec(keyBytes, "AES");
        Arrays.fill(keyBytes, (byte) 0);
    }

    @Override
    public String encrypt(String plaintext) {
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        byte[] plaintextBytes = plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintextBytes);
            return "v1:" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Bank data encryption failed", exception);
        } finally {
            Arrays.fill(plaintextBytes, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
        }
    }

    @Override
    public String mask(String ciphertext, int visibleDigits) {
        String plaintext = decrypt(ciphertext);
        int visible = Math.min(visibleDigits, plaintext.length());
        String suffix = plaintext.substring(plaintext.length() - visible);
        return "*".repeat(Math.max(4, plaintext.length() - visible)) + suffix;
    }

    private String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith("v1:")) {
            throw new IllegalStateException("Bank data encryption format is not supported");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(ciphertext.substring(3));
            if (payload.length <= NONCE_LENGTH) throw new IllegalStateException("Bank data ciphertext is invalid");
            byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(payload, NONCE_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] plaintext = cipher.doFinal(encrypted);
            try {
                return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(plaintext, (byte) 0);
                Arrays.fill(nonce, (byte) 0);
                Arrays.fill(encrypted, (byte) 0);
                Arrays.fill(payload, (byte) 0);
            }
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Bank data could not be decrypted", exception);
        }
    }
}
