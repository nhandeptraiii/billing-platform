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
        userRepository.findByEmail("admin@viettel.com").orElseGet(() -> {
            User admin = new User();
            admin.setFullName("System Administrator");
            admin.setEmail("admin@viettel.com");
            admin.setPassword(passwordEncoder.encode("123456"));
            admin.setStatus("ACTIVE");
            admin.setRole(RoleEnum.MANAGER);
            return userRepository.save(admin);
        });
    }
}
