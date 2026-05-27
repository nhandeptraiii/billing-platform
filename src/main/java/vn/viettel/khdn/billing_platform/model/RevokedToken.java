package vn.viettel.khdn.billing_platform.model;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {

    @Id
    private String tokenId; // JWT jti claim

    private String username;

    private Instant expirationTime;

    private Instant revokedAt;
}
