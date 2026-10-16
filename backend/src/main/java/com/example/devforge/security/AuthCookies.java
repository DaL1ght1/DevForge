package com.example.devforge.security;

import com.example.devforge.dto.AuthResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookies {
  public static final String ACCESS = "df_at";
  public static final String REFRESH = "df_rt";

  @Value("${devforge.cookies.secure:false}")
  private boolean secure;

  @Value("${devforge.cookies.refresh-ttl:PT30M}")
  private Duration refreshTtl;

  public void write(HttpServletResponse res, AuthResponse a) {
    add(res, ACCESS, a.accessToken(), Duration.ofSeconds(a.expiresIn()), "/api/v1");
    add(res, REFRESH, a.refreshToken(), refreshTtl, "/api/v1/auth");
  }

  public void clear(HttpServletResponse res) {
    add(res, ACCESS, "", Duration.ZERO, "/api/v1");
    add(res, REFRESH, "", Duration.ZERO, "/api/v1/auth");
  }

  private void add(HttpServletResponse res, String name, String value, Duration ttl, String path) {
    ResponseCookie c =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path(path)
            .maxAge(ttl)
            .build();
    res.addHeader(HttpHeaders.SET_COOKIE, c.toString());
  }
}
