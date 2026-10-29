package com.example.devforge.security;

import com.example.devforge.config.github.GithubAppProperties;
import com.example.devforge.exception.GithubIntegrationException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TokenCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKey key;
  private final SecureRandom random = new SecureRandom();

  public TokenCipher(GithubAppProperties props) {
    this.key = parseKey(props.tokenEncryptionKey());
  }

  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder()
          .encodeToString(
              ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
    } catch (GeneralSecurityException e) {
      throw new GithubIntegrationException("Token encryption failed", e);
    }
  }

  public String decrypt(String encoded) {
    try {
      byte[] data = Base64.getDecoder().decode(encoded);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
      byte[] plaintext = cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new GithubIntegrationException("Token decryption failed", e);
    }
  }

  private SecretKey requireKey() {
    if (key == null) {
      throw new GithubIntegrationException("GITHUB_TOKEN_ENCRYPTION_KEY is not configured");
    }
    return key;
  }

  private static SecretKey parseKey(String base64) {
    if (!StringUtils.hasText(base64)) {
      return null;
    }
    byte[] bytes = Base64.getDecoder().decode(base64.trim());
    if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
      throw new IllegalArgumentException(
          "GITHUB_TOKEN_ENCRYPTION_KEY must be base64 of 16, 24 or 32 bytes");
    }
    return new SecretKeySpec(bytes, "AES");
  }
}
