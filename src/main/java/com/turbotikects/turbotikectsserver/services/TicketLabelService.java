package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.SaveTicketLabelRequestDto;
import com.turbotikects.turbotikectsserver.dto.TicketLabelDto;
import com.turbotikects.turbotikectsserver.entitys.DynamicTranslationsEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketLabelEntity;
import com.turbotikects.turbotikectsserver.repositorys.DynamicTranslationsRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketLabelRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TicketLabelService {

    private static final String LABEL_TRANS_TYPE = "label";
    private static final String DEFAULT_LANG     = "en";

    private final TicketLabelRepository labelRepo;
    private final DynamicTranslationsRepository translationsRepo;

    public TicketLabelService(TicketLabelRepository labelRepo,
                              DynamicTranslationsRepository translationsRepo) {
        this.labelRepo        = labelRepo;
        this.translationsRepo = translationsRepo;
    }

    public List<TicketLabelDto> getAll() {
        List<TicketLabelEntity> labels = labelRepo.findAll();
        Map<String, String> names = translationsRepo
                .findAllByLangCodeAndType(DEFAULT_LANG, LABEL_TRANS_TYPE)
                .map(list -> list.stream().collect(
                        Collectors.toMap(t -> t.getTranslationKey(), t -> t.getTranslatedText())))
                .orElse(Map.of());

        return labels.stream().map(l -> toDto(l, names.getOrDefault(l.getLabelKey(), l.getLabelKey()))).toList();
    }

    @Transactional
    public TicketLabelDto create(SaveTicketLabelRequestDto req) {
        String key = sanitizeKey(req.getLabelKey());
        if (labelRepo.existsByLabelKey(key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Label key already exists: " + key);
        }
        TicketLabelEntity entity = new TicketLabelEntity();
        entity.setLabelKey(key);
        entity.setColor(req.getColor() != null ? req.getColor() : "#3b82f6");
        entity = labelRepo.save(entity);
        upsertTranslation(key, req.getName() != null ? req.getName() : key);
        return toDto(entity, req.getName() != null ? req.getName() : key);
    }

    @Transactional
    public TicketLabelDto update(Long id, SaveTicketLabelRequestDto req) {
        TicketLabelEntity entity = labelRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (req.getColor() != null) entity.setColor(req.getColor());
        entity = labelRepo.save(entity);
        String name = req.getName() != null ? req.getName() : entity.getLabelKey();
        upsertTranslation(entity.getLabelKey(), name);
        return toDto(entity, name);
    }

    @Transactional
    public void delete(Long id) {
        TicketLabelEntity entity = labelRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        translationsRepo.deleteByTypeAndTranslationKey(LABEL_TRANS_TYPE, entity.getLabelKey());
        labelRepo.deleteById(id);
    }

    @Transactional
    public void bulkDelete(List<Long> ids) {
        List<TicketLabelEntity> labels = labelRepo.findAllById(ids);
        for (TicketLabelEntity l : labels) {
            translationsRepo.deleteByTypeAndTranslationKey(LABEL_TRANS_TYPE, l.getLabelKey());
        }
        labelRepo.deleteAllByIdIn(ids);
    }

    private void upsertTranslation(String key, String name) {
        DynamicTranslationsEntity trans = translationsRepo
                .findByLangCodeAndTypeAndTranslationKey(DEFAULT_LANG, LABEL_TRANS_TYPE, key)
                .orElseGet(DynamicTranslationsEntity::new);
        trans.setLangCode(DEFAULT_LANG);
        trans.setType(LABEL_TRANS_TYPE);
        trans.setTranslationKey(key);
        trans.setTranslatedText(name);
        translationsRepo.save(trans);
    }

    private TicketLabelDto toDto(TicketLabelEntity e, String name) {
        TicketLabelDto dto = new TicketLabelDto();
        dto.setId(e.getId());
        dto.setLabelKey(e.getLabelKey());
        dto.setColor(e.getColor());
        dto.setName(name);
        return dto;
    }

    private String sanitizeKey(String raw) {
        if (raw == null || raw.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Label key is required");
        return raw.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
    }
}
