package vn.viettel.khdn.billing_platform.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import jakarta.validation.Valid;
import java.util.Map;

import vn.viettel.khdn.billing_platform.model.dto.ReqChangePasswordDTO;
import vn.viettel.khdn.billing_platform.model.dto.ReqResetPasswordDTO;
import vn.viettel.khdn.billing_platform.model.dto.ReqUserCreateDTO;
import vn.viettel.khdn.billing_platform.model.dto.ReqUserUpdateDTO;
import vn.viettel.khdn.billing_platform.model.dto.ReqUserUpdateMeDTO;
import vn.viettel.khdn.billing_platform.model.dto.ResUserDTO;
import vn.viettel.khdn.billing_platform.model.enums.RoleEnum;
import vn.viettel.khdn.billing_platform.service.UserService;
import vn.viettel.khdn.billing_platform.util.SecurityUtil;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<ResUserDTO> getCurrentUserEndpoint() {
        String username = SecurityUtil.getCurrentUserLogin()
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Chưa đăng nhập"));
        return ResponseEntity.ok(userService.getByUsername(username));
    }

    private ResUserDTO getCurrentUser() {
        String username = SecurityUtil.getCurrentUserLogin()
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Chưa đăng nhập"));
        return userService.getByUsername(username);
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me")
    public ResponseEntity<ResUserDTO> updateCurrentUser(@Valid @RequestBody ReqUserUpdateMeDTO req) {
        String username = SecurityUtil.getCurrentUserLogin()
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Chưa đăng nhập"));
        return ResponseEntity.ok(userService.updateMe(username, req));
    }

    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<Page<ResUserDTO>> getUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Order.asc("fullName")));

        RoleEnum roleEnum = null;
        if (role != null && !role.trim().isEmpty()) {
            try {
                roleEnum = RoleEnum.valueOf(role.trim().toUpperCase());
            } catch (Exception ignored) {
            }
        }
        return ResponseEntity.ok(userService.searchUsers(roleEnum, keyword, pageable));
    }

    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ResUserDTO> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    @PostMapping
    public ResponseEntity<ResUserDTO> create(@Valid @RequestBody ReqUserCreateDTO req) {
        ResUserDTO currentUser = getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(req, currentUser));
    }

    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ResUserDTO> update(@PathVariable("id") Long id,
                                       @Valid @RequestBody ReqUserUpdateDTO req) {
        ResUserDTO currentUser = getCurrentUser();
        return ResponseEntity.ok(userService.update(id, req, currentUser));
    }

    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ResUserDTO> setStatus(@PathVariable("id") Long id,
                                          @RequestParam("status") String status) {
        return ResponseEntity.ok(userService.setStatus(id, status));
    }

    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ReqChangePasswordDTO req) {
        String username = SecurityUtil.getCurrentUserLogin()
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Chưa đăng nhập"));
        ResUserDTO currentUser = userService.getByUsername(username);
        userService.changePassword(currentUser.id(), req.oldPassword(), req.newPassword());
        return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công!"));
    }

    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    @PutMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable("id") Long id,
            @Valid @RequestBody ReqResetPasswordDTO req) {
        userService.resetPassword(id, req.newPassword());
        return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công!"));
    }
    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    @PostMapping(value = "/import-consultants", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importConsultants(@RequestParam("file") MultipartFile file) {
        ResUserDTO currentUser = getCurrentUser();
        int count = userService.importConsultants(file, currentUser);
        return ResponseEntity.ok(Map.of(
            "message", "Import thành công " + count + " tư vấn viên",
            "importedCount", count
        ));
    }
}
