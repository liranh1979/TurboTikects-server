package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.McpServerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface McpServerRepository extends JpaRepository<McpServerEntity, Long> {

    List<McpServerEntity> findByEnabledTrue();

    boolean existsByPort(Integer port);
}
