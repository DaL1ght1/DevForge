package com.example.devforge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

public class CsrfHeaderFilter extends OncePerRequestFilter {
  private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, @NonNull HttpServletResponse res, @NonNull FilterChain chain)
      throws ServletException, IOException {
    boolean cookieAuth =
        req.getCookies() != null
            && Arrays.stream(req.getCookies())
                .anyMatch(
                    c ->
                        AuthCookies.ACCESS.equals(c.getName())
                            || AuthCookies.REFRESH.equals(c.getName()));
    if (cookieAuth
        && !SAFE.contains(req.getMethod())
        && req.getHeader("X-Requested-With") == null) {
      res.sendError(HttpServletResponse.SC_FORBIDDEN, "Missing X-Requested-With header");
      return;
    }
    chain.doFilter(req, res);
  }
}
