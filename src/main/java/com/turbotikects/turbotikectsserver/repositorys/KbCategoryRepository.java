package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.KbCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KbCategoryRepository extends JpaRepository<KbCategoryEntity, Long> {
    List<KbCategoryEntity> findAllByOrderByDisplayOrderAsc();
}
