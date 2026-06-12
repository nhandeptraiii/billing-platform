package vn.viettel.khdn.billing_platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.viettel.khdn.billing_platform.model.Region;

import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, Long> {
    Optional<Region> findByCode(String code);
}
