package vn.viettel.khdn.billing_platform.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import vn.viettel.khdn.billing_platform.model.enums.RoleEnum;

public record ReqUserCreateDTO(
    @NotBlank(message = "Username không được để trống")
    String username,

    @NotBlank(message = "Họ tên không được để trống")
    String fullName,


    @Pattern(regexp = "^\\d{10}$", message = "Số điện thoại phải gồm đúng 10 chữ số")
    String phone,

    @NotBlank(message = "Mật khẩu không được để trống")
    String password,

    @NotNull(message = "Role không được để trống")
    RoleEnum role,

    Long regionId
) {}
