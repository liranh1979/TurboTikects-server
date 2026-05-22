package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FieldDefinitionsRepository extends JpaRepository<FieldDefinitionsEntity, Long> {
    List<FieldDefinitionsEntity> findByIsSystemFalseOrderByDisplayOrder();
}