package ro.usv.rrm.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ro.usv.rrm.auth.dto.LoginRequest;
import ro.usv.rrm.auth.dto.LoginResponse;
import ro.usv.rrm.user.UserService;
import ro.usv.rrm.user.dto.UserResponse;

/** Endpoint-urile de autentificare (tag-ul {@code Autentificare} din contract). */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final SessionService sessionService;
  private final UserService userService;

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  /** Închide sesiunea curentă; tokenul ei primește apoi 401 la orice cerere. */
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@AuthenticationPrincipal AuthenticatedUser user) {
    sessionService.revoke(user.sessionTokenId());
  }

  /** Utilizatorul autentificat, cu rolurile sale. */
  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
    return userService.findById(user.userId());
  }
}
