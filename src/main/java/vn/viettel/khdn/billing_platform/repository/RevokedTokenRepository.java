package vn.viettel.khdn.billing_platform.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.viettel.khdn.billing_platform.model.RevokedToken;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    Optional<RevokedToken> findByTokenId(String tokenId);

    boolean existsByTokenId(String tokenId);
}
