package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.DynamicTranslationsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;



public interface DynamicTranslationsRepository  extends JpaRepository<DynamicTranslationsEntity,Long>{

   Optional<List<DynamicTranslationsEntity>> findAllByLangCodeAndType(String langCode, String type);

   List<DynamicTranslationsEntity> findByLangCode(String langCode);

   void deleteByLangCode(String langCode);

   Optional<DynamicTranslationsEntity> findByLangCodeAndTypeAndTranslationKey(String langCode, String type, String translationKey);

   @org.springframework.transaction.annotation.Transactional
   @org.springframework.data.jpa.repository.Modifying
   @org.springframework.data.jpa.repository.Query("DELETE FROM DynamicTranslationsEntity d WHERE d.type = :type AND d.translationKey = :translationKey")
   void deleteByTypeAndTranslationKey(String type, String translationKey);
}
