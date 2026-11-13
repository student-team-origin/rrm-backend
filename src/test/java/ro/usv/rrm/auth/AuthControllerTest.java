package ro.usv.rrm.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.usv.rrm.auth.dto.AuthTokens;
import ro.usv.rrm.auth.dto.LoginRequest;
import ro.usv.rrm.auth.dto.LoginResponse;
import ro.usv.rrm.common.config.RrmProperties;
import ro.usv.rrm.common.config.SecurityConfig;
import ro.usv.rrm.common.error.ApiException;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(RrmProperties.class)
class AuthControllerTest {

  private static final String LOGIN_JSON =
      """
      {"email": "andrei.popescu@student.usv.ro", "parola": "parola123"}
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;

  @Test
  void login_validCredentials_returns200WithToken() throws Exception {
    given(authService.login(new LoginRequest("andrei.popescu@student.usv.ro", "parola123", null)))
        .willReturn(LoginResponse.authenticated(new AuthTokens("jwt", 28800, null)));

    mockMvc
        .perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.twoFactorRequired").value(false))
        .andExpect(jsonPath("$.tokens.accessToken").value("jwt"))
        .andExpect(jsonPath("$.tokens.expiresIn").value(28800))
        .andExpect(jsonPath("$.tokens.deviceToken").doesNotExist())
        .andExpect(jsonPath("$.challengeId").doesNotExist());
  }

  @Test
  void login_twoFactorEnabled_returns200WithChallenge() throws Exception {
    given(authService.login(any())).willReturn(LoginResponse.twoFactorChallenge("challenge"));

    mockMvc
        .perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.twoFactorRequired").value(true))
        .andExpect(jsonPath("$.challengeId").value("challenge"))
        .andExpect(jsonPath("$.tokens").doesNotExist());
  }

  @Test
  void login_wrongPassword_returns401() throws Exception {
    given(authService.login(any()))
        .willThrow(
            ApiException.unauthorized("CREDENTIALE_INVALIDE", "E-mail sau parolă incorecte."));

    mockMvc
        .perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("CREDENTIALE_INVALIDE"))
        .andExpect(jsonPath("$.message").value("E-mail sau parolă incorecte."));
  }

  @Test
  void login_missingFields_returns400WithFieldErrors() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"nu-e-email\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DATE_INVALIDE"))
        .andExpect(jsonPath("$.fields.email").value("E-mailul nu are un format valid."))
        .andExpect(jsonPath("$.fields.parola").value("Parola este obligatorie."));
    then(authService).should(never()).login(any());
  }
}
