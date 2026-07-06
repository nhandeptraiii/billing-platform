package vn.viettel.khdn.billing_platform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.viettel.khdn.billing_platform.model.Region;
import vn.viettel.khdn.billing_platform.repository.RegionRepository;

import java.util.List;

@RestController
@RequestMapping("/regions")
@SuppressWarnings("null")
public class RegionController {

    private final RegionRepository regionRepository;

    public RegionController(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN', 'CONSULTANT')")
    public ResponseEntity<List<Region>> getAllRegions() {
        return ResponseEntity.ok(regionRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Region> createRegion(@RequestBody Region region) {
        return ResponseEntity.ok(regionRepository.save(region));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Region> updateRegion(@PathVariable Long id, @RequestBody Region region) {
        Region existing = regionRepository.findById(id)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy khu vực"));
        existing.setName(region.getName());
        return ResponseEntity.ok(regionRepository.save(existing));
    }
}
