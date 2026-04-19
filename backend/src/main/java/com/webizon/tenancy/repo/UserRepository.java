package com.webizon.tenancy.repo;

import com.webizon.tenancy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByKeycloakId(UUID keycloakId);

    Optional<User> findByEmailIgnoreCase(String email);

    @Query(value = """
           SELECT u FROM User u
           WHERE lower(u.fullName) LIKE :pattern
              OR lower(u.email) LIKE :pattern
           ORDER BY u.fullName ASC
           """)
    List<User> searchByNameOrEmail(@Param("pattern") String pattern, org.springframework.data.domain.Pageable pageable);

    default List<User> searchByNameOrEmail(String pattern, int limit) {
        return searchByNameOrEmail(pattern, org.springframework.data.domain.PageRequest.of(0, limit));
    }
}
