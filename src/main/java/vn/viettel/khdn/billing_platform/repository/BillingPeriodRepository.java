package vn.viettel.khdn.billing_platform.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.viettel.khdn.billing_platform.model.BillingPeriod;

public interface BillingPeriodRepository extends JpaRepository<BillingPeriod, Long> {

    Optional<BillingPeriod> findByMonthAndYear(Integer month, Integer year);

    boolean existsByMonthAndYear(Integer month, Integer year);
}
