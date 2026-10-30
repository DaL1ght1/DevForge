package com.example.devforge.security;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.devforge.exception.InvalidCredentialsException;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InternalApiGuard {

  private final String internalApiToken;

  public InternalApiGuard(@Value("${devforge.internal.api-token:}") String internalApiToken) {
    this.internalApiToken = internalApiToken;
  }

  public void require(String providedToken) {
    if (!StringUtils.hasText(internalApiToken)
        || providedToken == null
        || !MessageDigest.isEqual(
            internalApiToken.getBytes(UTF_8), providedToken.getBytes(UTF_8))) {
      throw new InvalidCredentialsException("Invalid internal API token");
    }
  }
}
