package vn.viettel.khdn.billing_platform.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.model.dto.ReqUserCreateDTO;
import vn.viettel.khdn.billing_platform.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public User getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng ID: " + id));
    }

    public User create(ReqUserCreateDTO req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new EntityExistsException("Email đã tồn tại: " + req.email());
        }
        User user = new User();
        user.setFullName(req.fullName());
        user.setEmail(req.email());
        user.setPhone(req.phone());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(req.role());
        user.setStatus("ACTIVE");
        return userRepository.save(user);
    }

    public User update(Long id, ReqUserCreateDTO req) {
        User user = getById(id);
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        user.setRole(req.role());
        if (req.password() != null && !req.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.password()));
        }
        return userRepository.save(user);
    }

    public User setStatus(Long id, String status) {
        User user = getById(id);
        if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            throw new IllegalArgumentException("Status không hợp lệ: " + status);
        }
        user.setStatus(status);
        return userRepository.save(user);
    }

    public void delete(Long id) {
        userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng ID: " + id));
        userRepository.deleteById(id);
    }
}
