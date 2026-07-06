package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<GroupEntity, Long> {

    Optional<GroupEntity> findByDisplayName(String displayName);

    @Query(value = "SELECT * FROM user_groups WHERE company_id = :companyId", nativeQuery = true)
    java.util.List<GroupEntity> findByCompanyId(@Param("companyId") Integer companyId);

    @Query(value = "SELECT COUNT(*) FROM user_groups WHERE company_id = :companyId", nativeQuery = true)
    long countByCompanyId(@Param("companyId") Integer companyId);

    /** Returns groups that have the TICKET_MANAGER permission — suitable for ticket assignment. */
    @Query(value = """
            SELECT DISTINCT g.* FROM user_groups g
            WHERE EXISTS (
              SELECT 1 FROM group_permissions gp
              JOIN permissions p ON p.id = gp.permission_id
              WHERE gp.group_id = g.ref_id AND p.permission_key = 'TICKET_MANAGER'
            )
            ORDER BY g.display_name
            """, nativeQuery = true)
    java.util.List<GroupEntity> findAssignableGroups();
}
