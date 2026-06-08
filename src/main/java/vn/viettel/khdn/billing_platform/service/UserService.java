package vn.viettel.khdn.billing_platform.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.model.dto.ReqUserCreateDTO;
import vn.viettel.khdn.billing_platform.model.dto.ReqUserUpdateDTO;
import vn.viettel.khdn.billing_platform.model.dto.ReqUserUpdateMeDTO;
import vn.viettel.khdn.billing_platform.model.dto.ResUserDTO;
import vn.viettel.khdn.billing_platform.model.enums.RoleEnum;
import vn.viettel.khdn.billing_platform.repository.UserRepository;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<ResUserDTO> getAll() {
        return userRepository.findAll().stream()
            .map(this::convertToResUserDTO)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ResUserDTO> searchUsers(RoleEnum role, String keyword, Pageable pageable) {
        return userRepository.searchUsers(role, keyword, pageable)
            .map(this::convertToResUserDTO);
    }

    @Transactional(readOnly = true)
    public ResUserDTO getById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng ID: " + id));
        return convertToResUserDTO(user);
    }

    @Transactional(readOnly = true)
    public ResUserDTO getByUsername(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng Username: " + username));
        return convertToResUserDTO(user);
    }

    public ResUserDTO create(ReqUserCreateDTO req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new EntityExistsException("Username đã tồn tại: " + req.username());
        }

        User user = new User();
        user.setUsername(req.username());
        user.setFullName(req.fullName());

        user.setPhone(req.phone());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(req.role());
        user.setStatus("ACTIVE");
        User saved = userRepository.save(user);
        return convertToResUserDTO(saved);
    }

    public ResUserDTO update(Long id, ReqUserUpdateDTO req) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng ID: " + id));
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        user.setRole(req.role());
        User saved = userRepository.save(user);
        return convertToResUserDTO(saved);
    }

    public ResUserDTO updateMe(String username, ReqUserUpdateMeDTO req) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng Username: " + username));
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        User saved = userRepository.save(user);
        return convertToResUserDTO(saved);
    }

    public ResUserDTO setStatus(Long id, String status) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng ID: " + id));
        if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            throw new IllegalArgumentException("Status không hợp lệ: " + status);
        }
        user.setStatus(status);
        User saved = userRepository.save(user);
        return convertToResUserDTO(saved);
    }

    public void delete(Long id) {
        userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng ID: " + id));
        userRepository.deleteById(id);
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng ID: " + userId));
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu cũ không đúng");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng ID: " + userId));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private ResUserDTO convertToResUserDTO(User user) {
        return new ResUserDTO(
            user.getId(),
            user.getUsername(),
            user.getFullName(),

            user.getPhone(),
            user.getStatus(),
            user.getRole(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
