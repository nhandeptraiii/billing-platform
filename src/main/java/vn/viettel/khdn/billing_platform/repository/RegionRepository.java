package vn.viettel.khdn.billing_platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.viettel.khdn.billing_platform.model.Region;

public interface RegionRepository extends JpaRepository<Region, Long> {
}
