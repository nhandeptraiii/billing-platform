package vn.viettel.khdn.billing_platform.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import vn.viettel.khdn.billing_platform.model.enums.RoleEnum;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 150)
    private String fullName;

    @Email(message = "Email không hợp lệ")
    @Column(length = 120, nullable = false, unique = true)
    private String email;

    @Pattern(regexp = "^\\d{10}$", message = "Số điện thoại phải gồm đúng 10 chữ số")
    @Column(length = 30)
    private String phone;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String password;

    @Column(length = 20)
    private String status = "ACTIVE"; // ACTIVE | INACTIVE

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RoleEnum role = RoleEnum.CONSULTANT;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    public void handleBeforeCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void handleBeforeUpdate() {
        this.updatedAt = Instant.now();
    }
}
