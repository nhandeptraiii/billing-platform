package vn.viettel.khdn.billing_platform.model.dto;

import vn.viettel.khdn.billing_platform.model.enums.RoleEnum;

public record ResLoginDTO(
    Long id,
    String fullName,
    String email,
    String phone,
    RoleEnum role,
    String accessToken
) {}
