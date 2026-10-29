package com.example.devforge.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.devforge.config.github.GithubAppProperties;
import com.example.devforge.exception.GithubIntegrationException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenCipherTest {

  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

  private static TokenCipher cipher(String key) {
    return new TokenCipher(new GithubAppProperties(null, null, null, null, key, null, null));
  }

  @Test
  void roundTripsAndUsesFreshIvEachTime() {
    TokenCipher cipher = cipher(KEY);

    String first = cipher.encrypt("ghu_secret");
    String second = cipher.encrypt("ghu_secret");

    assertThat(first).isNotEqualTo(second).doesNotContain("ghu_secret");
    assertThat(cipher.decrypt(first)).isEqualTo("ghu_secret");
    assertThat(cipher.decrypt(second)).isEqualTo("ghu_secret");
  }

  @Test
  void tamperedCiphertextIsRejected() {
    TokenCipher cipher = cipher(KEY);
    byte[] data = Base64.getDecoder().decode(cipher.encrypt("ghu_secret"));
    data[data.length - 1] ^= 1;

    assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(data)))
        .isInstanceOf(GithubIntegrationException.class);
  }

  @Test
  void missingKeyRefusesToEncrypt() {
    assertThatThrownBy(() -> cipher(null).encrypt("ghu_secret"))
        .isInstanceOf(GithubIntegrationException.class);
  }

  @Test
  void wrongKeyLengthFailsFast() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[10]);
    assertThatThrownBy(() -> cipher(shortKey)).isInstanceOf(IllegalArgumentException.class);
  }
}
