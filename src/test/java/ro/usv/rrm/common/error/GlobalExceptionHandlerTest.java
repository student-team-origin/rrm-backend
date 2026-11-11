package ro.usv.rrm.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void notFoundException_returns404WithCode() throws Exception {
    mockMvc
        .perform(get("/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESURSA_INEXISTENTA"))
        .andExpect(jsonPath("$.fields").doesNotExist());
  }

  @Test
  void conflictException_returns409() throws Exception {
    mockMvc.perform(get("/conflict")).andExpect(status().isConflict());
  }

  @Test
  void businessRuleException_returns422() throws Exception {
    mockMvc.perform(get("/business-rule")).andExpect(status().isUnprocessableContent());
  }

  @Test
  void invalidBody_returns400WithFieldErrors() throws Exception {
    mockMvc
        .perform(
            post("/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DATE_INVALIDE"))
        .andExpect(jsonPath("$.fields.name").exists());
  }

  @Test
  void malformedJson_returns400() throws Exception {
    mockMvc
        .perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CERERE_INVALIDA"));
  }

  record TestRequest(@NotBlank String name) {}

  @RestController
  static class TestController {

    @GetMapping("/not-found")
    void notFound() {
      throw ApiException.notFound("RESURSA_INEXISTENTA", "Resursa nu există.");
    }

    @GetMapping("/conflict")
    void conflict() {
      throw ApiException.conflict("RESURSA_ARE_REZERVARI", "Resursa are rezervări.");
    }

    @GetMapping("/business-rule")
    void businessRule() {
      throw ApiException.businessRule("SOLD_INSUFICIENT", "Sold insuficient.");
    }

    @PostMapping("/validate")
    void validate(@Valid @RequestBody TestRequest request) {}
  }
}
