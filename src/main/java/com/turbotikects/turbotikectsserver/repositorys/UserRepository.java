package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsernameAndPassword(String username, String password);

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByLdapExternalId(String ldapExternalId);

    Optional<UserEntity> findByAzureExternalId(String azureExternalId);

    List<UserEntity> findBySourceType(int sourceType);

    Optional<UserEntity> findByEmail(String email);
}
