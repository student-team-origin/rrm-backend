package ro.usv.rrm.resource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.usv.rrm.common.config.RrmProperties;
import ro.usv.rrm.common.config.SecurityConfig;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.common.web.PageResponse;
import ro.usv.rrm.resource.dto.ChangeResourceStatusRequest;
import ro.usv.rrm.resource.dto.ChangeResourceStatusResponse;
import ro.usv.rrm.resource.dto.CreateResourceRequest;
import ro.usv.rrm.resource.dto.ResourceCategoryResponse;
import ro.usv.rrm.resource.dto.ResourceResponse;

@WebMvcTest(ResourceController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(RrmProperties.class)
class ResourceControllerTest {

  private static final ResourceResponse RESOURCE =
      new ResourceResponse(
          1L,
          "Braț robotic UR5",
          "6 axe",
          ResourceStatus.ACTIVE,
          15,
          "Necesită instruire SSM",
          new ResourceCategoryResponse(2L, "Echipament"),
          1L);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ResourceService resourceService;

  @Test
  void getResources_withFilters_passesThemAndReturnsPage() throws Exception {
    ResourceFilter filter = new ResourceFilter("robot", 2L, 1L, ResourceStatus.ACTIVE);
    given(resourceService.search(filter, 0, 10))
        .willReturn(new PageResponse<>(List.of(RESOURCE), 0, 10, 1));

    mockMvc
        .perform(
            get("/api/v1/resources")
                .param("q", "robot")
                .param("categorieId", "2")
                .param("laboratorId", "1")
                .param("stare", "Activa")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].denumire").value("Braț robotic UR5"))
        .andExpect(jsonPath("$.items[0].stare").value("Activa"))
        .andExpect(jsonPath("$.items[0].timpPregatireMin").value(15))
        .andExpect(jsonPath("$.items[0].categorie.denumire").value("Echipament"))
        .andExpect(jsonPath("$.items[0].laboratorId").value(1));
  }

  @Test
  void getResources_unknownStatus_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/resources").param("stare", "Necunoscuta"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CERERE_INVALIDA"));
  }

  @Test
  void getResource_unknownId_returns404() throws Exception {
    given(resourceService.findById(99L))
        .willThrow(ApiException.notFound("RESURSA_INEXISTENTA", "Resursa nu există."));

    mockMvc
        .perform(get("/api/v1/resources/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESURSA_INEXISTENTA"))
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void createResource_validBody_returns201WithLocation() throws Exception {
    given(resourceService.create(any(CreateResourceRequest.class))).willReturn(RESOURCE);

    mockMvc
        .perform(
            post("/api/v1/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"denumire": "Braț robotic UR5", "categorieId": 2, "laboratorId": 1,
                     "timpPregatireMin": 15}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "http://localhost/api/v1/resources/1"))
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.stare").value("Activa"));
  }

  @Test
  void createResource_invalidBody_returns400WithFields() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"denumire\": \"\", \"timpPregatireMin\": -5}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DATE_INVALIDE"))
        .andExpect(jsonPath("$.fields.denumire").exists())
        .andExpect(jsonPath("$.fields.categorieId").exists())
        .andExpect(jsonPath("$.fields.laboratorId").exists())
        .andExpect(jsonPath("$.fields.timpPregatireMin").exists());
  }

  @Test
  void deleteResource_withoutReservations_returns204() throws Exception {
    mockMvc.perform(delete("/api/v1/resources/3")).andExpect(status().isNoContent());

    then(resourceService).should().delete(3L);
  }

  @Test
  void deleteResource_withReservations_returns409() throws Exception {
    willThrow(ApiException.conflict("RESURSA_ARE_REZERVARI", "Resursa are rezervări."))
        .given(resourceService)
        .delete(1L);

    mockMvc
        .perform(delete("/api/v1/resources/1"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RESURSA_ARE_REZERVARI"));
  }

  @Test
  void changeResourceStatus_validBody_returns200() throws Exception {
    given(
            resourceService.changeStatus(
                eq(1L), eq(new ChangeResourceStatusRequest(ResourceStatus.MAINTENANCE, null))))
        .willReturn(new ChangeResourceStatusResponse(RESOURCE, 0));

    mockMvc
        .perform(
            patch("/api/v1/resources/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stare\": \"Mentenanta\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resursa.id").value(1))
        .andExpect(jsonPath("$.rezervariAnulate").value(0));
  }

  @Test
  void changeResourceStatus_missingStatus_returns400WithFields() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/resources/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motiv\": \"Calibrare\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.stare").exists());
  }
}
