package vn.viettel.khdn.billing_platform.model;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Cấu hình thông tin cửa hàng dùng để in bill.
 * Bảng này chỉ có 1 dòng duy nhất (singleton config).
 */
@Getter
@Setter
@Entity
@Table(name = "store_config")
public class StoreConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String storeName; // Tên cửa hàng / chi nhánh

    @Column(length = 500)
    private String address; // Địa chỉ cửa hàng

    @Column(length = 20)
    private String hotline; // Số điện thoại hotline

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", unique = true)
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private User updatedBy;

    private Instant updatedAt;

    @PreUpdate
    public void handleBeforeUpdate() {
        this.updatedAt = Instant.now();
    }
}
