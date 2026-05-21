package vn.viettel.khdn.billing_platform.model.dto;

import jakarta.validation.constraints.NotBlank;

public record ReqResetPasswordDTO(
    @NotBlank(message = "Mật khẩu mới không được để trống")
    String newPassword
) {}
