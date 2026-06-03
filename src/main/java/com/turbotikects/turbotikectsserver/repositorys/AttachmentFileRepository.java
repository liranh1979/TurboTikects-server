package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.AttachmentFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttachmentFileRepository extends JpaRepository<AttachmentFileEntity, Long> {
    Optional<AttachmentFileEntity> findByContentHash(String hash);
}
