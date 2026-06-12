package vn.viettel.khdn.billing_platform.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.viettel.khdn.billing_platform.model.StoreConfig;

public interface StoreConfigRepository extends JpaRepository<StoreConfig, Long> {

    Optional<StoreConfig> findByRegionId(Long regionId);
    
    // Fallback if region is null
    Optional<StoreConfig> findFirstByOrderByIdAsc();
}
