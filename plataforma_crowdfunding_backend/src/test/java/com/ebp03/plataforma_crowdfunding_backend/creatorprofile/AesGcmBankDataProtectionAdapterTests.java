package com.ebp03.plataforma_crowdfunding_backend.creatorprofile;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.service.AesGcmBankDataProtectionAdapter;
import org.junit.jupiter.api.Test;

class AesGcmBankDataProtectionAdapterTests {
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void encryptsWithRandomizedAuthenticatedCiphertextAndMasksPlaintext() {
        var adapter = new AesGcmBankDataProtectionAdapter(KEY);
        String first = adapter.encrypt("1234567890");
        String second = adapter.encrypt("1234567890");

        assertNotEquals(first, second);
        assertTrue(first.startsWith("v1:"));
        assertTrue(!first.contains("1234567890"));
        assertTrue(adapter.mask(first, 4).endsWith("7890"));
        assertTrue(adapter.mask(first, 4).startsWith("******"));
    }

    @Test
    void refusesMissingOrWeakEncryptionKeys() {
        assertThrows(IllegalStateException.class, () -> new AesGcmBankDataProtectionAdapter(""));
        assertThrows(IllegalStateException.class, () -> new AesGcmBankDataProtectionAdapter("dG9vIHNob3J0"));
    }

    @Test
    void rejectsCiphertextTampering() {
        var adapter = new AesGcmBankDataProtectionAdapter(KEY);
        String encrypted = adapter.encrypt("1234567890");
        String tampered = encrypted.substring(0, encrypted.length() - 1)
                + (encrypted.endsWith("A") ? "B" : "A");

        assertThrows(IllegalStateException.class, () -> adapter.mask(tampered, 4));
    }
}
