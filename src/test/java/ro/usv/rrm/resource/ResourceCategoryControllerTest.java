package ro.usv.rrm.resource;

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
import ro.usv.rrm.resource.dto.ResourceCategoryResponse;

@WebMvcTest(ResourceCategoryController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(RrmProperties.class)
class ResourceCategoryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ResourceCategoryService categoryService;

  @Test
  void getResourceCategories_returns200WithContractFields() throws Exception {
    given(categoryService.findAll())
        .willReturn(
            List.of(
                new ResourceCategoryResponse(2L, "Echipament"),
                new ResourceCategoryResponse(4L, "Nod de calcul")));

    mockMvc
        .perform(get("/api/v1/resource-categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(2))
        .andExpect(jsonPath("$[0].denumire").value("Echipament"));
  }
}
