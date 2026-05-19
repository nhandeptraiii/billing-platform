package vn.viettel.khdn.billing_platform.config.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import vn.viettel.khdn.billing_platform.service.RevokedTokenService;

/**
 * Validator kiểm tra JWT có bị thu hồi (logout) chưa.
 * Tích hợp vào JwtDecoder trong SecurityConfiguration.
 */
public class RevokedTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final RevokedTokenService revokedTokenService;

    public RevokedTokenValidator(RevokedTokenService revokedTokenService) {
        this.revokedTokenService = revokedTokenService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String tokenId = token.getId(); // jti claim
        if (tokenId != null && revokedTokenService.isRevoked(tokenId)) {
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("token_revoked", "Token đã bị thu hồi (đã logout)", null)
            );
        }
        return OAuth2TokenValidatorResult.success();
    }
}
