package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.EmailAlreadyInUseException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.InvalidCredentialsException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.InvalidEmailException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.InvalidOAuthCodeException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.InvalidRoleException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.WeakPasswordException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidPathParameter(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(new ApiError(
                "INVALID_PARAMETER",
                "El parámetro " + exception.getName() + " debe ser un UUID válido."));
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    ResponseEntity<ApiError> emailAlreadyInUse() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("EMAIL_ALREADY_IN_USE", "El correo ya está en uso."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> duplicateConstraint() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("EMAIL_ALREADY_IN_USE", "El correo ya está en uso."));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("INVALID_CREDENTIALS", "Las credenciales no son válidas."));
    }

    @ExceptionHandler(InvalidEmailException.class)
    ResponseEntity<ApiError> invalidEmail() {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "El correo no es válido."));
    }

    @ExceptionHandler(WeakPasswordException.class)
    ResponseEntity<ApiError> weakPassword(WeakPasswordException exception) {
        return ResponseEntity.badRequest().body(new ApiError("WEAK_PASSWORD", "La contraseña no cumple los requisitos.", exception.getRequirements()));
    }

    @ExceptionHandler(InvalidRoleException.class)
    ResponseEntity<ApiError> invalidRole() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_ROLE", "El rol debe ser creator o sponsor."));
    }

    @ExceptionHandler(InvalidOAuthCodeException.class)
    ResponseEntity<ApiError> invalidOAuthCode() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_OAUTH_CODE", "El código OAuth no es válido."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "La solicitud no es válida.", details));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> statusError(ResponseStatusException exception) {
        String reason = exception.getReason() == null ? "La solicitud no es válida." : exception.getReason();
        String code = reason.matches("[A-Z][A-Z0-9_]+") ? reason : "REQUEST_ERROR";
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(code, code.equals("REQUEST_ERROR") ? reason : "La operación no pudo completarse."));
    }
}
