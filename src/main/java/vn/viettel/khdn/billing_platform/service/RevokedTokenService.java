package vn.viettel.khdn.billing_platform.service;

import org.springframework.stereotype.Service;

import vn.viettel.khdn.billing_platform.repository.RevokedTokenRepository;

@Service
public class RevokedTokenService {

    private final RevokedTokenRepository revokedTokenRepository;

    public RevokedTokenService(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public boolean isRevoked(String tokenId) {
        return revokedTokenRepository.existsByTokenId(tokenId);
    }
}
