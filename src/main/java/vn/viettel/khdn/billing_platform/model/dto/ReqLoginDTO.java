package vn.viettel.khdn.billing_platform.model.dto;

import jakarta.validation.constraints.NotBlank;

public record ReqLoginDTO(
    @NotBlank(message = "Username không được để trống")
    String username,

    @NotBlank(message = "Mật khẩu không được để trống")
    String password
) {}
