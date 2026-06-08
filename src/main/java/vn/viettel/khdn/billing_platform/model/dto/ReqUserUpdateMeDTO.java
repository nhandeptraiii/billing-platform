package vn.viettel.khdn.billing_platform.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReqUserUpdateMeDTO(
    @NotBlank(message = "Họ tên không được để trống")
    String fullName,

    @Pattern(regexp = "^\\d{10}$", message = "Số điện thoại phải gồm đúng 10 chữ số")
    String phone
) {}
