package ro.usv.rrm.resource;

import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ro.usv.rrm.common.web.PageResponse;
import ro.usv.rrm.resource.dto.ChangeResourceStatusRequest;
import ro.usv.rrm.resource.dto.ChangeResourceStatusResponse;
import ro.usv.rrm.resource.dto.CreateResourceRequest;
import ro.usv.rrm.resource.dto.ResourceResponse;

/** CRUD-ul catalogului de resurse (tag-ul {@code Resurse} din contract). */
@RestController
@RequestMapping("/resources")
@RequiredArgsConstructor
public class ResourceController {

  private final ResourceService resourceService;

  @GetMapping
  public PageResponse<ResourceResponse> getResources(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Long categorieId,
      @RequestParam(required = false) Long laboratorId,
      @RequestParam(required = false) ResourceStatus stare,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return resourceService.search(
        new ResourceFilter(q, categorieId, laboratorId, stare), page, size);
  }

  @GetMapping("/{resourceId}")
  public ResourceResponse getResource(@PathVariable Long resourceId) {
    return resourceService.findById(resourceId);
  }

  @PostMapping
  public ResponseEntity<ResourceResponse> createResource(
      @Valid @RequestBody CreateResourceRequest request) {
    ResourceResponse created = resourceService.create(request);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @PutMapping("/{resourceId}")
  public ResourceResponse updateResource(
      @PathVariable Long resourceId, @Valid @RequestBody CreateResourceRequest request) {
    return resourceService.update(resourceId, request);
  }

  @DeleteMapping("/{resourceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteResource(@PathVariable Long resourceId) {
    resourceService.delete(resourceId);
  }

  @PatchMapping("/{resourceId}/status")
  public ChangeResourceStatusResponse changeResourceStatus(
      @PathVariable Long resourceId, @Valid @RequestBody ChangeResourceStatusRequest request) {
    return resourceService.changeStatus(resourceId, request);
  }
}
