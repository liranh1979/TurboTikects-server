package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.DynamicTranslationsSystemFieldsDto;
import com.turbotikects.turbotikectsserver.entitys.DynamicTranslationsEntity;
import com.turbotikects.turbotikectsserver.repositorys.DynamicTranslationsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DynamicTranslationsService {

  @Autowired
  private DynamicTranslationsRepository dynamicTranslationsRepository;

    @Cacheable(cacheNames = "system_fields", key = "#langCode")
    public Map<String,String> getSystemFields(String langCode){

        Map<String,String> systemFields = new HashMap<>();
        Optional<List<DynamicTranslationsEntity>> systemFieldsRepository =  dynamicTranslationsRepository.findAllByLangCodeAndType(langCode, "system");
        if(systemFieldsRepository.isPresent()){

            for(DynamicTranslationsEntity dynamicTranslations : systemFieldsRepository.get()){
                systemFields.put(dynamicTranslations.getTranslationKey(),dynamicTranslations.getTranslatedText());
            }
        }
        return systemFields;
    }
}
