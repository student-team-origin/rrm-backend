package ro.usv.rrm.laboratory;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.usv.rrm.laboratory.dto.LaboratoryResponse;

/** Endpoint-urile de citire pentru laboratoare (tag-ul {@code Laboratoare} din contract). */
@RestController
@RequestMapping("/labs")
@RequiredArgsConstructor
public class LaboratoryController {

  private final LaboratoryService laboratoryService;

  @GetMapping
  public List<LaboratoryResponse> getLabs() {
    return laboratoryService.findAll();
  }

  @GetMapping("/{labId}")
  public LaboratoryResponse getLab(@PathVariable Long labId) {
    return laboratoryService.findById(labId);
  }
}
