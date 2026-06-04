package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.TemplateVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersionEntity, Long> {
    Optional<TemplateVersionEntity> findByTemplateIdAndIsCurrentTrue(Long templateId);
    List<TemplateVersionEntity> findByTemplateIdOrderByVersionNumberDesc(Long templateId);
}
