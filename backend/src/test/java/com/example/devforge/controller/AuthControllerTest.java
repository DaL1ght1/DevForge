package com.example.devforge.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.devforge.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock private AuthService authService;

  @InjectMocks private AuthController authController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(authController)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
  }

  @Test
  void shouldLogoutSuccessfullyWithRefreshToken() throws Exception {
    String json =
        """
                {
                    "refreshToken": "sample-refresh-token"
                }
                """;

    mockMvc
        .perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isNoContent());

    verify(authService).logout(eq("sample-refresh-token"), any());
  }

  @Test
  void shouldLogoutSuccessfullyWithoutBody() throws Exception {
    mockMvc.perform(post("/auth/logout")).andExpect(status().isNoContent());

    verify(authService).logout(eq(null), any());
  }
}
