package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.service;

public interface BankDataProtectionPort {
    String encrypt(String plaintext);
    String mask(String ciphertext, int visibleDigits);
}
