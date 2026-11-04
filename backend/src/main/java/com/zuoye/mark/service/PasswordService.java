package com.zuoye.mark.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private final SecureRandom random = new SecureRandom();

    public String encode(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt);
        return "pbkdf2$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt)
                + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    public boolean matches(String password, String encoded) {
        if (password == null || encoded == null) return false;
        String[] parts = encoded.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) return false;
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception error) {
            return false;
        }
    }

    private byte[] pbkdf2(String password, byte[] salt) {
        return pbkdf2(password, salt, ITERATIONS);
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    salt,
                    iterations,
                    KEY_LENGTH);
            return SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (Exception error) {
            throw new IllegalStateException("密码哈希失败。", error);
        }
    }
}
