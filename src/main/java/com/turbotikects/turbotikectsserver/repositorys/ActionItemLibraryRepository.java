package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.ActionItemLibraryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActionItemLibraryRepository extends JpaRepository<ActionItemLibraryEntity, Long> {

    List<ActionItemLibraryEntity> findAllByOrderByUpdatedAtDesc();

    List<ActionItemLibraryEntity> findByTypeOrderByUpdatedAtDesc(String type);

    List<ActionItemLibraryEntity> findByStatusOrderByUpdatedAtDesc(String status);

    List<ActionItemLibraryEntity> findByTypeAndStatusOrderByUpdatedAtDesc(String type, String status);
}
