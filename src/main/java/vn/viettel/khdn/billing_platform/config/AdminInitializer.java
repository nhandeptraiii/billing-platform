package vn.viettel.khdn.billing_platform.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.model.enums.RoleEnum;
import vn.viettel.khdn.billing_platform.repository.UserRepository;

/**
 * Tự động tạo tài khoản admin mặc định khi khởi động lần đầu.
 */
@Component
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        userRepository.findByUsername("admin").ifPresentOrElse(
            existingAdmin -> {
                // Nếu admin đang là MANAGER (do bug cũ), nâng cấp lên ADMIN
                if (existingAdmin.getRole() != RoleEnum.ADMIN) {
                    existingAdmin.setRole(RoleEnum.ADMIN);
                    userRepository.save(existingAdmin);
                }
            },
            () -> {
                User admin = new User();
                admin.setUsername("admin");
                admin.setFullName("System Administrator");
                admin.setPassword(passwordEncoder.encode("123456"));
                admin.setStatus("ACTIVE");
                admin.setRole(RoleEnum.ADMIN);
                userRepository.save(admin);
            }
        );
    }
}
