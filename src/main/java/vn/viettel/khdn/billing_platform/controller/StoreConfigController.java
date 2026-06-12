package vn.viettel.khdn.billing_platform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.EntityNotFoundException;
import vn.viettel.khdn.billing_platform.model.StoreConfig;
import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.repository.StoreConfigRepository;
import vn.viettel.khdn.billing_platform.repository.UserRepository;
import vn.viettel.khdn.billing_platform.repository.RegionRepository;
import vn.viettel.khdn.billing_platform.model.Region;
import vn.viettel.khdn.billing_platform.util.SecurityUtil;

@RestController
@RequestMapping("/store-config")
public class StoreConfigController {

    private final StoreConfigRepository storeConfigRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;

    public StoreConfigController(StoreConfigRepository storeConfigRepository,
                                 UserRepository userRepository,
                                 RegionRepository regionRepository) {
        this.storeConfigRepository = storeConfigRepository;
        this.userRepository = userRepository;
        this.regionRepository = regionRepository;
    }

    private User getCurrentUser() {
        String username = SecurityUtil.getCurrentUserLogin()
            .orElseThrow(() -> new EntityNotFoundException("Chưa đăng nhập"));
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng"));
    }

    /** GET /store-config — Lấy cấu hình in bill */
    @GetMapping
    public ResponseEntity<StoreConfig> getConfig(@RequestParam(value = "regionId", required = false) Long regionId) {
        User currentUser = getCurrentUser();
        Long targetRegionId = regionId != null ? regionId : (currentUser.getRegion() != null ? currentUser.getRegion().getId() : null);
        
        if (targetRegionId != null) {
            return ResponseEntity.ok(
                storeConfigRepository.findByRegionId(targetRegionId)
                    .orElse(new StoreConfig()));
        }
        return ResponseEntity.ok(
            storeConfigRepository.findFirstByOrderByIdAsc()
                .orElse(new StoreConfig()));
    }

    /** PUT /store-config — Cập nhật cấu hình (Manager only) */
    @PutMapping
    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    public ResponseEntity<StoreConfig> updateConfig(@RequestBody StoreConfig incoming, @RequestParam(value = "regionId", required = false) Long regionId) {
        User currentUser = getCurrentUser();
        Long targetRegionId = regionId != null ? regionId : (currentUser.getRegion() != null ? currentUser.getRegion().getId() : null);

        StoreConfig config;
        if (targetRegionId != null) {
            config = storeConfigRepository.findByRegionId(targetRegionId).orElse(new StoreConfig());
            Region region = regionRepository.findById(targetRegionId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khu vực"));
            config.setRegion(region);
        } else {
            config = storeConfigRepository.findFirstByOrderByIdAsc().orElse(new StoreConfig());
        }

        config.setStoreName(incoming.getStoreName());
        config.setAddress(incoming.getAddress());
        config.setHotline(incoming.getHotline());
        config.setUpdatedBy(currentUser);

        return ResponseEntity.ok(storeConfigRepository.save(config));
    }
}
