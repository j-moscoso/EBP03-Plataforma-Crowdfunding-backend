package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import com.ebp03.plataforma_crowdfunding_backend.auth.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) { this.service = service; }

    @PostMapping("/request")
    public ResponseEntity<PasswordResetResponses.GenericMessage> request(
            @RequestBody PasswordResetRequests.Request request, HttpServletRequest httpRequest) {
        return ResponseEntity.accepted().body(service.request(request.email(), httpRequest.getRemoteAddr(), userAgent(httpRequest)));
    }

    @PostMapping("/validate")
    public PasswordResetResponses.Validation validate(
            @Valid @RequestBody PasswordResetRequests.Validate request, HttpServletRequest httpRequest) {
        return service.validate(request.token(), httpRequest.getRemoteAddr(), userAgent(httpRequest));
    }

    @PostMapping("/confirm")
    public PasswordResetResponses.Confirmation confirm(
            @Valid @RequestBody PasswordResetRequests.Confirm request, HttpServletRequest httpRequest) {
        return service.confirm(request.token(), request.newPassword(), httpRequest.getRemoteAddr(), userAgent(httpRequest));
    }

    private String userAgent(HttpServletRequest request) { return request.getHeader("User-Agent"); }
}
