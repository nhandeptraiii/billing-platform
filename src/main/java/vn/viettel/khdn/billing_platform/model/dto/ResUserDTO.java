package vn.viettel.khdn.billing_platform.model.dto;

import java.time.Instant;
import vn.viettel.khdn.billing_platform.model.enums.RoleEnum;

public record ResUserDTO(
    Long id,
    String username,
    String fullName,
    String email,
    String phone,
    String status,
    RoleEnum role,
    Instant createdAt,
    Instant updatedAt
) {}
