package vn.viettel.khdn.billing_platform.model.dto;

import jakarta.validation.constraints.NotBlank;

public record ReqChangePasswordDTO(
    @NotBlank(message = "Mật khẩu cũ không được để trống")
    String oldPassword,

    @NotBlank(message = "Mật khẩu mới không được để trống")
    String newPassword
) {}
