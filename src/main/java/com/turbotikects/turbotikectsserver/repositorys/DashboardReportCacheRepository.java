package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.DashboardReportCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DashboardReportCacheRepository extends JpaRepository<DashboardReportCacheEntity, Long> {

    // Plain "=" on a nullable companyId never matches NULL in JPQL, so the null case is handled explicitly
    @Query("SELECT c FROM DashboardReportCacheEntity c " +
           "WHERE c.dashboardId = :dashboardId " +
           "AND ((:companyId IS NULL AND c.companyId IS NULL) OR c.companyId = :companyId)")
    Optional<DashboardReportCacheEntity> findByDashboardIdAndCompanyId(@Param("dashboardId") String dashboardId,
                                                                        @Param("companyId") Integer companyId);
}
