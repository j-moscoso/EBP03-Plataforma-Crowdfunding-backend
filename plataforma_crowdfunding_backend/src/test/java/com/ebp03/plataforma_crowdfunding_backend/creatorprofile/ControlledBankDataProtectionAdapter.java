package com.ebp03.plataforma_crowdfunding_backend.creatorprofile;

import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.service.BankDataProtectionPort;
import java.util.HashMap;
import java.util.Map;

final class ControlledBankDataProtectionAdapter implements BankDataProtectionPort {
    private final Map<String, String> values = new HashMap<>();
    private int nextToken;

    @Override
    public String encrypt(String plaintext) {
        String token = "controlled-token-" + nextToken++;
        values.put(token, plaintext);
        return token;
    }

    @Override
    public String mask(String ciphertext, int visibleDigits) {
        String plaintext = values.get(ciphertext);
        int visible = Math.min(visibleDigits, plaintext.length());
        return "*".repeat(Math.max(4, plaintext.length() - visible))
                + plaintext.substring(plaintext.length() - visible);
    }
}
