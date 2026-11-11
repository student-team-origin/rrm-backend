package ro.usv.rrm.laboratory;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.usv.rrm.common.config.RrmProperties;
import ro.usv.rrm.common.config.SecurityConfig;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.laboratory.dto.LaboratoryResponse;

@WebMvcTest(LaboratoryController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(RrmProperties.class)
class LaboratoryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private LaboratoryService laboratoryService;

  @Test
  void getLabs_returns200WithContractFields() throws Exception {
    given(laboratoryService.findAll())
        .willReturn(
            List.of(
                new LaboratoryResponse(2L, "Laborator Electronică", "Corp C, sala C201", 3L),
                new LaboratoryResponse(1L, "Laborator Robotică", "Corp C, sala C204", 2L)));

    mockMvc
        .perform(get("/api/v1/labs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(2))
        .andExpect(jsonPath("$[0].denumire").value("Laborator Electronică"))
        .andExpect(jsonPath("$[0].locatie").value("Corp C, sala C201"))
        .andExpect(jsonPath("$[0].responsabilId").value(3));
  }

  @Test
  void getLab_existingId_returns200() throws Exception {
    given(laboratoryService.findById(1L))
        .willReturn(new LaboratoryResponse(1L, "Laborator Robotică", "Corp C, sala C204", 2L));

    mockMvc
        .perform(get("/api/v1/labs/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.denumire").value("Laborator Robotică"));
  }

  @Test
  void getLab_unknownId_returns404() throws Exception {
    given(laboratoryService.findById(99L))
        .willThrow(ApiException.notFound("LABORATOR_INEXISTENT", "Laboratorul nu există."));

    mockMvc
        .perform(get("/api/v1/labs/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("LABORATOR_INEXISTENT"))
        .andExpect(jsonPath("$.message").exists());
  }
}
