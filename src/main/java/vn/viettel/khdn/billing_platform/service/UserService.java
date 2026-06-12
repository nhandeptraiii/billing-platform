package vn.viettel.khdn.billing_platform.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import java.io.InputStream;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.model.dto.ReqUserCreateDTO;
import vn.viettel.khdn.billing_platform.model.dto.ReqUserUpdateDTO;
import vn.viettel.khdn.billing_platform.model.dto.ReqUserUpdateMeDTO;
import vn.viettel.khdn.billing_platform.model.dto.ResUserDTO;
import vn.viettel.khdn.billing_platform.model.enums.RoleEnum;
import vn.viettel.khdn.billing_platform.repository.UserRepository;
import vn.viettel.khdn.billing_platform.repository.RegionRepository;
import vn.viettel.khdn.billing_platform.model.Region;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RegionRepository regionRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.regionRepository = regionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<ResUserDTO> getAll() {
        return userRepository.findAll().stream()
            .map(this::convertToResUserDTO)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ResUserDTO> searchUsers(ResUserDTO currentUser, RoleEnum role, String keyword, Pageable pageable) {
        Long regionId = currentUser.role() == RoleEnum.ADMIN ? null : currentUser.regionId();
        return userRepository.searchUsers(regionId, role, keyword, pageable)
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

    public ResUserDTO create(ReqUserCreateDTO req, ResUserDTO currentUser) {
        if (userRepository.existsByUsername(req.username())) {
            throw new EntityExistsException("Username đã tồn tại: " + req.username());
        }

        User user = new User();
        user.setUsername(req.username());
        user.setFullName(req.fullName());

        user.setPhone(req.phone());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(req.role());

        if (currentUser.role() == RoleEnum.MANAGER) {
            Region region = regionRepository.findById(currentUser.regionId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khu vực của Manager"));
            user.setRegion(region);
        } else {
            if (req.regionId() != null) {
                Region region = regionRepository.findById(req.regionId())
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khu vực ID: " + req.regionId()));
                user.setRegion(region);
            } else if (req.role() != RoleEnum.ADMIN) {
                throw new IllegalArgumentException("Khu vực không được để trống khi tạo " + req.role());
            }
        }

        user.setStatus("ACTIVE");
        User saved = userRepository.save(user);
        return convertToResUserDTO(saved);
    }

    public ResUserDTO update(Long id, ReqUserUpdateDTO req, ResUserDTO currentUser) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng ID: " + id));
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        user.setRole(req.role());

        if (currentUser.role() == RoleEnum.MANAGER) {
            Region region = regionRepository.findById(currentUser.regionId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khu vực của Manager"));
            user.setRegion(region);
        } else {
            if (req.regionId() != null) {
                Region region = regionRepository.findById(req.regionId())
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khu vực ID: " + req.regionId()));
                user.setRegion(region);
            } else if (req.role() != RoleEnum.ADMIN) {
                throw new IllegalArgumentException("Khu vực không được để trống khi cập nhật " + req.role());
            } else {
                user.setRegion(null);
            }
        }

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

    public int importConsultants(MultipartFile file, ResUserDTO currentUser) {
        if (currentUser.role() != RoleEnum.MANAGER && currentUser.role() != RoleEnum.ADMIN) {
            throw new IllegalArgumentException("Chỉ Quản lý hoặc Admin mới được import nhân viên");
        }

        Region region = null;
        if (currentUser.role() == RoleEnum.MANAGER) {
            region = regionRepository.findById(currentUser.regionId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khu vực của Manager"));
        }

        int count = 0;
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String username = getCellValue(row.getCell(0));
                String fullName = getCellValue(row.getCell(1));
                String phone = getCellValue(row.getCell(2));

                if (username.isEmpty() || fullName.isEmpty()) {
                    continue; // Bỏ qua các dòng thiếu dữ liệu bắt buộc
                }

                if (phone == null || !phone.matches("^0\\d{9,10}$")) {
                    continue; // Bỏ qua nếu SĐT không hợp lệ (bắt đầu bằng 0, độ dài 10-11 số)
                }

                if (userRepository.existsByUsername(username)) {
                    continue; // Bỏ qua user đã tồn tại
                }

                User user = new User();
                user.setUsername(username);
                user.setFullName(fullName);
                user.setPhone(phone);
                user.setPassword(passwordEncoder.encode("123456"));
                user.setRole(RoleEnum.CONSULTANT);
                user.setStatus("ACTIVE");

                if (region != null) {
                    user.setRegion(region); // Manager import -> gán khu vực của Manager
                } else {
                    user.setRegion(null); // Admin import -> Tạm thời không có khu vực
                }

                userRepository.save(user);
                count++;
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi đọc file Excel: " + e.getMessage(), e);
        }
        return count;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> "";
        };
    }

    private ResUserDTO convertToResUserDTO(User user) {
        return new ResUserDTO(
            user.getId(),
            user.getUsername(),
            user.getFullName(),

            user.getPhone(),
            user.getStatus(),
            user.getRole(),
            user.getRegion() != null ? user.getRegion().getId() : null,
            user.getRegion() != null ? user.getRegion().getName() : null,
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
