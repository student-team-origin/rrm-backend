package ro.usv.rrm.approval;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.usv.rrm.approval.dto.ApprovalResponse;
import ro.usv.rrm.approval.dto.DecisionRequest;
import ro.usv.rrm.approval.dto.RejectionRequest;
import ro.usv.rrm.auth.AuthenticatedUser;
import ro.usv.rrm.reservation.dto.ReservationDetailResponse;

/** Fluxul de aprobare pe 2 niveluri (tag-ul {@code Aprobări} din contract). */
@RestController
@RequestMapping("/approvals")
@RequiredArgsConstructor
public class ApprovalController {

  private final ApprovalFlowService approvalFlowService;

  @GetMapping
  public List<ReservationDetailResponse> getApprovals(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(defaultValue = "inAsteptare") ApprovalListStatus stare) {
    return approvalFlowService.findForApprover(user.userId(), stare);
  }

  @PostMapping("/{reservationId}/approve")
  public ApprovalResponse approve(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable Long reservationId,
      @Valid @RequestBody(required = false) DecisionRequest request) {
    String observation = request == null ? null : request.observatie();
    return approvalFlowService.approve(reservationId, user.userId(), observation);
  }

  @PostMapping("/{reservationId}/reject")
  public ApprovalResponse reject(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable Long reservationId,
      @Valid @RequestBody RejectionRequest request) {
    return approvalFlowService.reject(reservationId, user.userId(), request.observatie());
  }
}
