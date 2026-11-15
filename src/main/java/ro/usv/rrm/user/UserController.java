package ro.usv.rrm.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.usv.rrm.common.web.PageResponse;
import ro.usv.rrm.user.dto.UserResponse;

/** Administrarea utilizatorilor (tag-ul {@code Utilizatori} din contract). */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  /** Lista utilizatorilor: doar Administratorul de sistem. */
  @GetMapping
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public PageResponse<UserResponse> getUsers(
      @RequestParam(required = false) Role rol,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return userService.search(rol, q, page, size);
  }

  /** Detaliile unui utilizator: Administratorul de sistem sau utilizatorul însuși. */
  @GetMapping("/{userId}")
  @PreAuthorize("@access.isSelfOrAdmin(#userId)")
  public UserResponse getUser(@PathVariable Long userId) {
    return userService.findById(userId);
  }
}
