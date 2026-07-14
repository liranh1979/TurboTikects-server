package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.SlaPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicyEntity, Long> {

    List<SlaPolicyEntity> findByIsActiveTrueOrderByPriorityAsc();

    /** Admin listing — includes inactive policies so they can be reactivated. */
    List<SlaPolicyEntity> findAllByOrderByPriorityAsc();

    /** Template-specific policy override, if one exists for this priority. */
    Optional<SlaPolicyEntity> findByPriorityAndTemplateIdAndIsActiveTrue(String priority, Long templateId);

    /** The priority's default policy (no template scoping). */
    Optional<SlaPolicyEntity> findByPriorityAndTemplateIdIsNullAndIsActiveTrue(String priority);
}
