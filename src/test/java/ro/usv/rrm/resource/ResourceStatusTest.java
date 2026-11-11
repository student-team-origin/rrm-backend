package ro.usv.rrm.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ResourceStatusTest {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @Test
  void fromValue_contractValues_mapToConstants() {
    assertThat(ResourceStatus.fromValue("Activa")).isEqualTo(ResourceStatus.ACTIVE);
    assertThat(ResourceStatus.fromValue("Mentenanta")).isEqualTo(ResourceStatus.MAINTENANCE);
    assertThat(ResourceStatus.fromValue("Defecta")).isEqualTo(ResourceStatus.BROKEN);
  }

  @Test
  void fromValue_unknownValue_throws() {
    assertThatThrownBy(() -> ResourceStatus.fromValue("ACTIVE"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void json_usesContractValues() {
    assertThat(jsonMapper.writeValueAsString(ResourceStatus.MAINTENANCE))
        .isEqualTo("\"Mentenanta\"");
    assertThat(jsonMapper.readValue("\"Defecta\"", ResourceStatus.class))
        .isEqualTo(ResourceStatus.BROKEN);
  }
}
