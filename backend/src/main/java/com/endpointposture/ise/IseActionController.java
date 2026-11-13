package com.endpointposture.ise;

import com.endpointposture.ise.dto.EnforcementRequest;
import com.endpointposture.ise.dto.ShareRequest;
import com.endpointposture.ise.dto.IseOperationResolutionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;

/**
 * The only place Cisco ISE enforcement is ever triggered from - three
 * separate, explicit, operator-initiated actions.
 *
 * <p>Deliberately its own controller, calling its own service bean
 * ({@link IseActionService}), never shared with
 * {@link com.endpointposture.posture.PostureIngestController} - so
 * "record a posture result" can never fall through into "call ISE"
 * (project plan Section 2.2 / 8.1). Every action returns {@code 200} on
 * success or {@code 502} if ISE rejected/failed the call, and every call
 * writes an audit row either way.</p>
 */
@RestController
@RequestMapping("/api/v1/ise")
@Tag(name = "ISE actions", description = "Explicit share/restrict/clear actions against Cisco ISE. Nothing here happens automatically.")

public class IseActionController {

    private final IseActionService service;
    private final IseOperationStore operations;

    public IseActionController(IseActionService service, IseOperationStore operations) {
        this.service = service;
        this.operations = operations;
    }

    /**
     * Shares an endpoint's latest posture with ISE.
     *
     * @param req  the endpoint to share
     * @param auth the authenticated caller, recorded on the audit row
     */
    @Operation(
            summary = "Share an endpoint's latest posture with ISE",
            description = "Writes the endpoint's most recent stored assessment into ISE as a custom "
                    + "endpoint attribute. ISE's own Authorization Policy decides what to do with it - "
                    + "this call makes no access decision itself."
    )
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','ANALYST')")
    @PostMapping("/posture/share")
    public ResponseEntity<IseResult> share(@Valid @RequestBody ShareRequest req, Authentication auth) {
        IseResult result = service.sharePosture(req.endpointId(), auth.getName());
        return ResponseEntity.status(result.success() ? 200 : 502).body(result);
    }

    /**
     * Requests an immediate restriction.
     *
     * @param req  the endpoint (and optional ANC policy override)
     * @param auth the authenticated caller
     */
    @Operation(
            summary = "Restrict an endpoint",
            description = "Requests an immediate CoA re-authentication or ANC quarantine action, "
                    + "depending on app.ise.enforcement-mode. This can disrupt the user's network "
                    + "access right away."
    )
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")

    @PostMapping("/enforcement/restrict")
    public ResponseEntity<IseResult> restrict(@Valid @RequestBody EnforcementRequest req, Authentication auth) {
        IseResult result = service.restrict(req.endpointId(), req.policy(), auth.getName());
        return ResponseEntity.status(result.success() ? 200 : 502).body(result);
    }

    /**
     * Clears a previously-applied restriction.
     *
     * @param req  the endpoint to clear
     * @param auth the authenticated caller
     */
    @Operation(summary = "Clear a restriction on an endpoint")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")

    @PostMapping("/enforcement/clear")
    public ResponseEntity<IseResult> clear(@Valid @RequestBody EnforcementRequest req, Authentication auth) {
        IseResult result = service.clearRestriction(req.endpointId(), auth.getName());
        return ResponseEntity.status(result.success() ? 200 : 502).body(result);
    }

    @Operation(summary = "List ISE operations that require manual reconciliation",
            description = "ISE ERS readback cannot reliably confirm the outcome of every supported action. Verify the endpoint in ISE before resolving.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/operations/reconciliation-required")
    public ResponseEntity<?> reconciliationRequired() {
        return ResponseEntity.ok(operations.pendingReconciliation(200));
    }

    @Operation(summary = "Resolve an ambiguous ISE operation after checking ISE")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/operations/{id}/resolve")
    public ResponseEntity<?> resolve(@PathVariable UUID id, @Valid @RequestBody IseOperationResolutionRequest request,
                                     Authentication auth) {
        boolean resolved = operations.resolve(id, auth.getName(), request.resolution(), request.supportingDetails());
        return resolved ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
