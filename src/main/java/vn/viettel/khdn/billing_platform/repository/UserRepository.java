package vn.viettel.khdn.billing_platform.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.model.enums.RoleEnum;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query(value = """
            SELECT u FROM User u
            WHERE (:role IS NULL OR u.role = :role)
              AND (
                :keyword IS NULL
                OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR u.phone LIKE CONCAT('%', :keyword, '%')
              )
            """)
    Page<User> searchUsers(
            @Param("role") RoleEnum role,
            @Param("keyword") String keyword,
            Pageable pageable);
}
