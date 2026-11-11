package ro.usv.rrm.laboratory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ro.usv.rrm.common.error.ApiException;

@ExtendWith(MockitoExtension.class)
class LaboratoryServiceTest {

  @Mock private LaboratoryRepository laboratoryRepository;

  @InjectMocks private LaboratoryService laboratoryService;

  @Test
  void findById_existingLab_returnsContractFields() {
    given(laboratoryRepository.findById(1L))
        .willReturn(Optional.of(new Laboratory("Laborator Robotică", "Corp C, sala C204", 2L)));

    var response = laboratoryService.findById(1L);

    assertThat(response.denumire()).isEqualTo("Laborator Robotică");
    assertThat(response.locatie()).isEqualTo("Corp C, sala C204");
    assertThat(response.responsabilId()).isEqualTo(2L);
  }

  @Test
  void findById_unknownLab_throwsNotFound() {
    given(laboratoryRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> laboratoryService.findById(99L))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.getCode()).isEqualTo("LABORATOR_INEXISTENT");
            });
  }
}
