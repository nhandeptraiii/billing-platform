package vn.viettel.khdn.billing_platform.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.viettel.khdn.billing_platform.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
