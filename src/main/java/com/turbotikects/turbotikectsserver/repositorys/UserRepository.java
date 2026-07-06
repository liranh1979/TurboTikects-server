package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsernameAndPassword(String username, String password);

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByLdapExternalId(String ldapExternalId);

    Optional<UserEntity> findByAzureExternalId(String azureExternalId);

    List<UserEntity> findBySourceType(int sourceType);

    Optional<UserEntity> findByEmail(String email);

    // Native SQL to bypass any Hibernate JPQL cache issue with newly-added column
    @Query(value = "SELECT * FROM users WHERE company_id = :companyId AND is_deleted = 0", nativeQuery = true)
    List<UserEntity> findByCompanyId(@Param("companyId") Integer companyId);

    @Query(value = "SELECT COUNT(*) FROM users WHERE company_id = :companyId AND is_deleted = 0", nativeQuery = true)
    long countByCompanyId(@Param("companyId") Integer companyId);

    List<UserEntity> findByIsDeletedFalse();

    /** Returns non-deleted users who are super admins OR have the TICKET_MANAGER permission — suitable for ticket assignment. */
    @Query(value = """
            SELECT DISTINCT u.* FROM users u
            WHERE u.is_deleted = 0
            AND (u.is_super_admin = 1
                 OR EXISTS (
                   SELECT 1 FROM user_permissions up
                   JOIN permissions p ON p.id = up.permission_id
                   WHERE up.user_id = u.red_id AND p.permission_key = 'TICKET_MANAGER'
                 ))
            ORDER BY COALESCE(u.display_name, u.user_name)
            """, nativeQuery = true)
    List<UserEntity> findAssignableUsers();
}
