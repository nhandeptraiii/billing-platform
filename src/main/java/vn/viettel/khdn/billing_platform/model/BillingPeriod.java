package vn.viettel.khdn.billing_platform.model;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.viettel.khdn.billing_platform.model.enums.BillingPeriodStatusEnum;

@Getter
@Setter
@Entity
@Table(
    name = "billing_periods",
    uniqueConstraints = @UniqueConstraint(columnNames = {"month", "year"})
)
public class BillingPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer month; // 1-12

    @Column(nullable = false)
    private Integer year;  // VD: 2026

    @Column(length = 50)
    private String name;   // "Tháng 5/2026" — tự sinh khi tạo

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BillingPeriodStatusEnum status = BillingPeriodStatusEnum.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    private Instant createdAt;

    @PrePersist
    public void handleBeforeCreate() {
        this.createdAt = Instant.now();
        if (this.name == null || this.name.isBlank()) {
            this.name = "Tháng " + this.month + "/" + this.year;
        }
    }
}
