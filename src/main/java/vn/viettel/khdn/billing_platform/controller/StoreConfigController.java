package vn.viettel.khdn.billing_platform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.EntityNotFoundException;
import vn.viettel.khdn.billing_platform.model.StoreConfig;
import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.repository.StoreConfigRepository;
import vn.viettel.khdn.billing_platform.repository.UserRepository;
import vn.viettel.khdn.billing_platform.util.SecurityUtil;

@RestController
@RequestMapping("/store-config")
public class StoreConfigController {

    private final StoreConfigRepository storeConfigRepository;
    private final UserRepository userRepository;

    public StoreConfigController(StoreConfigRepository storeConfigRepository,
                                 UserRepository userRepository) {
        this.storeConfigRepository = storeConfigRepository;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        String username = SecurityUtil.getCurrentUserLogin()
            .orElseThrow(() -> new EntityNotFoundException("Chưa đăng nhập"));
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng"));
    }

    /** GET /store-config — Lấy cấu hình in bill */
    @GetMapping
    public ResponseEntity<StoreConfig> getConfig() {
        return ResponseEntity.ok(
            storeConfigRepository.findFirstByOrderByIdAsc()
                .orElse(new StoreConfig()));
    }

    /** PUT /store-config — Cập nhật cấu hình (Manager only) */
    @PutMapping
    @PreAuthorize("hasAuthority('MANAGER')")
    public ResponseEntity<StoreConfig> updateConfig(@RequestBody StoreConfig incoming) {
        User currentUser = getCurrentUser();
        StoreConfig config = storeConfigRepository.findFirstByOrderByIdAsc()
            .orElse(new StoreConfig());

        config.setStoreName(incoming.getStoreName());
        config.setAddress(incoming.getAddress());
        config.setHotline(incoming.getHotline());
        config.setAdsText(incoming.getAdsText());
        config.setUpdatedBy(currentUser);

        return ResponseEntity.ok(storeConfigRepository.save(config));
    }
}
