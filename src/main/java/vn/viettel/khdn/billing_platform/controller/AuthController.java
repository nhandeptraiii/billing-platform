package vn.viettel.khdn.billing_platform.controller;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.model.dto.ReqLoginDTO;
import vn.viettel.khdn.billing_platform.model.dto.ResLoginDTO;
import vn.viettel.khdn.billing_platform.model.dto.ResUserDTO;
import vn.viettel.khdn.billing_platform.service.AuthService;
import vn.viettel.khdn.billing_platform.util.SecurityUtil;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ResLoginDTO> login(@Valid @RequestBody ReqLoginDTO req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // Lấy JWT hiện tại để blacklist
        SecurityUtil.getCurrentUserJwt().ifPresent(jwt -> {
            String tokenId = jwt.getId();
            String username = jwt.getSubject();
            Instant expiresAt = jwt.getExpiresAt();
            authService.logout(tokenId, username, expiresAt);
        });
        return ResponseEntity.ok().build();
    }

    @GetMapping("/account")
    public ResponseEntity<ResUserDTO> getAccount() {
        String username = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Chưa đăng nhập"));
        User user = authService.getCurrentUser(username);
        ResUserDTO res = new ResUserDTO(
                user.getId(),
                user.getUsername(),
                user.getFullName(),

                user.getPhone(),
                user.getStatus(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt());
        return ResponseEntity.ok(res);
    }
}
