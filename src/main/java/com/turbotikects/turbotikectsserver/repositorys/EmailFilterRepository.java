package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.EmailFilterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailFilterRepository extends JpaRepository<EmailFilterEntity, Long> {

    List<EmailFilterEntity> findByMailboxId(Long mailboxId);

    List<EmailFilterEntity> findByMailboxIdIsNull();
}
