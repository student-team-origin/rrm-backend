package ro.usv.rrm.grant;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.usv.rrm.auth.AuthenticatedUser;
import ro.usv.rrm.grant.dto.GrantResponse;

/** Granturile de cercetare (tag-ul {@code Granturi} din contract). Deocamdată doar citirea. */
@RestController
@RequestMapping("/grants")
@RequiredArgsConstructor
public class GrantController {

  private final GrantService grantService;

  /** Granturile din care face parte utilizatorul curent (toate, pentru administrator). */
  @GetMapping
  public List<GrantResponse> getGrants(@AuthenticationPrincipal AuthenticatedUser user) {
    return grantService.findVisibleTo(user);
  }

  /** Detaliile unui grant: administratorul, coordonatorul și membrii lui; altfel 403. */
  @GetMapping("/{grantId}")
  @PreAuthorize("@grantAccess.canView(#grantId)")
  public GrantResponse getGrant(@PathVariable Long grantId) {
    return grantService.findById(grantId);
  }
}
