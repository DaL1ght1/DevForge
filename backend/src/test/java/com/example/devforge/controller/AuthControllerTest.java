package com.example.devforge.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.devforge.dto.AuthResponse;
import com.example.devforge.security.AuthCookies;
import com.example.devforge.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock private AuthService authService;
  @Mock private AuthCookies cookies;

  @InjectMocks private AuthController authController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
  }

  @Test
  void logoutUsesRefreshTokenFromCookieAndClearsCookies() throws Exception {
    mockMvc
        .perform(
            post("/auth/logout").cookie(new Cookie(AuthCookies.REFRESH, "sample-refresh-token")))
        .andExpect(status().isNoContent());

    verify(authService).logout(eq("sample-refresh-token"), isNull());
    verify(cookies).clear(any());
  }

  @Test
  void logoutWithoutCookieStillSucceedsAndClearsCookies() throws Exception {
    mockMvc.perform(post("/auth/logout")).andExpect(status().isNoContent());

    verify(authService).logout(isNull(), isNull());
    verify(cookies).clear(any());
  }

  @Test
  void loginSetsCookiesAndDoesNotReturnTokensInBody() throws Exception {
    when(authService.login(any()))
        .thenReturn(new AuthResponse("access", "refresh", 300, "Bearer", null));

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"user","password":"valid-password"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expiresIn").value(300))
        .andExpect(jsonPath("$.accessToken").doesNotExist())
        .andExpect(jsonPath("$.refreshToken").doesNotExist());

    verify(cookies).write(any(), any(AuthResponse.class));
  }
}
