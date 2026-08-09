package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.AlertTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertTypeRepository extends JpaRepository<AlertTypeEntity, Long> {
    List<AlertTypeEntity> findAllByOrderByDisplayOrderAsc();
    Optional<AlertTypeEntity> findByTypeKey(String typeKey);
    boolean existsByTypeKey(String typeKey);
}
