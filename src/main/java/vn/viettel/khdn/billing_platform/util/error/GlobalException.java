package vn.viettel.khdn.billing_platform.util.error;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import vn.viettel.khdn.billing_platform.model.RestResponse;

@RestControllerAdvice
public class GlobalException {

    @ExceptionHandler({
        IllegalArgumentException.class,
        IllegalStateException.class,
        EntityExistsException.class
    })
    public ResponseEntity<RestResponse<Object>> handleBadRequest(RuntimeException ex) {
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.BAD_REQUEST.value());
        res.setError(ex.getClass().getSimpleName());
        res.setMessage(ex.getMessage());
        return ResponseEntity.badRequest().body(res);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<RestResponse<Object>> handleNotFound(EntityNotFoundException ex) {
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.NOT_FOUND.value());
        res.setError("EntityNotFoundException");
        res.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(res);
    }

    @ExceptionHandler({ UsernameNotFoundException.class, BadCredentialsException.class })
    public ResponseEntity<RestResponse<Object>> handleAuthException(Exception ex) {
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.BAD_REQUEST.value());
        res.setError("Bad Credentials");
        res.setMessage("Email hoặc mật khẩu không chính xác");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<RestResponse<Object>> handleDisabledAccount(DisabledException ex) {
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.UNAUTHORIZED.value());
        res.setError("DisabledAccount");
        res.setMessage("Tài khoản đã bị khóa. Vui lòng liên hệ quản lý.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestResponse<Object>> validation(MethodArgumentNotValidException ex) {
        BindingResult result = ex.getBindingResult();
        final List<FieldError> fieldErrors = result.getFieldErrors();

        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.BAD_REQUEST.value());
        res.setError("VALIDATION_ERROR");

        List<Map<String, String>> errors = fieldErrors.stream()
                .map(f -> {
                    Map<String, String> errorMap = new HashMap<>();
                    errorMap.put("field", f.getField());
                    errorMap.put("message", f.getDefaultMessage());
                    return errorMap;
                })
                .collect(Collectors.toList());

        res.setMessage(errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RestResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.BAD_REQUEST.value());
        res.setError("ConstraintViolation");

        List<String> messages = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());
        res.setMessage(messages.size() > 1 ? messages : messages.get(0));

        return ResponseEntity.badRequest().body(res);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<RestResponse<Object>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.FORBIDDEN.value());
        res.setError("AccessDeniedException");
        res.setMessage("Bạn không có quyền truy cập hoặc thực hiện thao tác này");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(res);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<RestResponse<Object>> handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.BAD_REQUEST.value());
        res.setError("MaxUploadSizeExceededException");
        res.setMessage("Dung lượng file upload vượt quá giới hạn cho phép");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
    }
}
