package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TicketRepository extends JpaRepository<TicketEntity, Long> {

    @Query("SELECT t FROM TicketEntity t WHERE (:cursor IS NULL OR t.id < :cursor) AND (:status IS NULL OR t.status = :status) AND (:templateId IS NULL OR t.templateId = :templateId) AND (:responsibleUserId IS NULL OR t.responsibleUserId = :responsibleUserId) AND (:filterUserId IS NULL OR t.requestUserId = :filterUserId) AND (:companyId IS NULL OR t.companyId = :companyId) ORDER BY t.id DESC LIMIT :size")
    List<TicketEntity> findPage(@Param("cursor") Long cursor, @Param("size") int size, @Param("status") String status, @Param("templateId") Long templateId, @Param("responsibleUserId") Integer responsibleUserId, @Param("filterUserId") Integer filterUserId, @Param("companyId") Integer companyId);

    @Query(value = "SELECT * FROM tickets WHERE MATCH(title, description) AGAINST (:q IN BOOLEAN MODE) AND (:cursor IS NULL OR id < :cursor) AND (:companyId IS NULL OR company_id = :companyId) ORDER BY id DESC LIMIT :size", nativeQuery = true)
    List<TicketEntity> searchPage(@Param("q") String q, @Param("cursor") Long cursor, @Param("size") int size, @Param("companyId") Integer companyId);

    @Query(value = "SELECT * FROM tickets WHERE MATCH(title, description) AGAINST (:q IN BOOLEAN MODE) AND (:cursor IS NULL OR id < :cursor) AND request_user_id = :filterUserId AND (:companyId IS NULL OR company_id = :companyId) ORDER BY id DESC LIMIT :size", nativeQuery = true)
    List<TicketEntity> searchPageByUser(@Param("q") String q, @Param("cursor") Long cursor, @Param("size") int size, @Param("filterUserId") Integer filterUserId, @Param("companyId") Integer companyId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TicketEntity t WHERE t.id IN :ids")
    void deleteByIdIn(@Param("ids") List<Long> ids);

    @Modifying
    @Transactional
    @Query("UPDATE TicketEntity t SET t.status = :status, t.updatedAt = CURRENT_TIMESTAMP WHERE t.id IN :ids")
    void updateStatusByIds(@Param("ids") List<Long> ids, @Param("status") String status);

    @Modifying
    @Transactional
    @Query("UPDATE TicketEntity t SET t.responsibleUserId = :userId, t.updatedAt = CURRENT_TIMESTAMP WHERE t.id IN :ids")
    void updateResponsibleUserByIds(@Param("ids") List<Long> ids, @Param("userId") Integer userId);

    @Modifying
    @Transactional
    @Query("UPDATE TicketEntity t SET t.responsibleGroupId = :groupId, t.updatedAt = CURRENT_TIMESTAMP WHERE t.id IN :ids")
    void updateResponsibleGroupByIds(@Param("ids") List<Long> ids, @Param("groupId") Integer groupId);
}
