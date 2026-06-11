package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.HoursOfOperationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface HoursOfOperationRepository extends JpaRepository<HoursOfOperationEntity, Integer> {

    List<HoursOfOperationEntity> findByCompanyIdIsNull();

    List<HoursOfOperationEntity> findByCompanyId(Integer companyId);

    @Modifying
    @Transactional
    @Query("DELETE FROM HoursOfOperationEntity h WHERE h.companyId = :companyId")
    void deleteByCompanyId(@Param("companyId") Integer companyId);

    @Modifying
    @Transactional
    @Query("DELETE FROM HoursOfOperationEntity h WHERE h.companyId IS NULL")
    void deleteGlobal();
}
