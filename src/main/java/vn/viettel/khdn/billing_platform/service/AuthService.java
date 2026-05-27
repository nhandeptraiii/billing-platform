package vn.viettel.khdn.billing_platform.service;

import java.time.Instant;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;
import vn.viettel.khdn.billing_platform.model.RevokedToken;
import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.model.dto.ReqLoginDTO;
import vn.viettel.khdn.billing_platform.model.dto.ResLoginDTO;
import vn.viettel.khdn.billing_platform.repository.RevokedTokenRepository;
import vn.viettel.khdn.billing_platform.repository.UserRepository;
import vn.viettel.khdn.billing_platform.util.SecurityUtil;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SecurityUtil securityUtil;
    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    public AuthService(AuthenticationManager authenticationManager,
            SecurityUtil securityUtil,
            UserRepository userRepository,
            RevokedTokenRepository revokedTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.securityUtil = securityUtil;
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public ResLoginDTO login(ReqLoginDTO req) {
        // Xác thực username + password
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));

        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new org.springframework.security.authentication.DisabledException(
                    "Tài khoản đã bị khóa");
        }

        String token = securityUtil.createToken(authentication);

        return new ResLoginDTO(
                user.getId(),
                user.getUsername(),
                user.getFullName(),

                user.getPhone(),
                user.getRole(),
                token);
    }

    public void logout(String tokenId, String username, Instant expiresAt) {
        RevokedToken revoked = new RevokedToken();
        revoked.setTokenId(tokenId);
        revoked.setUsername(username);
        revoked.setExpirationTime(expiresAt);
        revoked.setRevokedAt(Instant.now());
        revokedTokenRepository.save(revoked);
    }

    public User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng: " + username));
    }
}
