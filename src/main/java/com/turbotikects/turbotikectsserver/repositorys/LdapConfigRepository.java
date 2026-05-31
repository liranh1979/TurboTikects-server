package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.LdapConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LdapConfigRepository extends JpaRepository<LdapConfigEntity, Long> {

    List<LdapConfigEntity> findByIsActiveTrue();
}
