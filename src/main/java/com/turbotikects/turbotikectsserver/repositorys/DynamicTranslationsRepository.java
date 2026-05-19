package com.turbotikects.turbotikectsserver.repositorys;

import com.turbotikects.turbotikectsserver.entitys.DynamicTranslationsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;



public interface DynamicTranslationsRepository  extends JpaRepository<DynamicTranslationsEntity,Long>{

   Optional<List<DynamicTranslationsEntity>> findAllByLangCodeAndType(String langCode, String type);
}
