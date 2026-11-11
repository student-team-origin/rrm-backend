package ro.usv.rrm.resource;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.usv.rrm.resource.dto.ResourceCategoryResponse;

/** Lista categoriilor de resurse (tag-ul {@code Resurse} din contract). */
@RestController
@RequestMapping("/resource-categories")
@RequiredArgsConstructor
public class ResourceCategoryController {

  private final ResourceCategoryService categoryService;

  @GetMapping
  public List<ResourceCategoryResponse> getResourceCategories() {
    return categoryService.findAll();
  }
}
