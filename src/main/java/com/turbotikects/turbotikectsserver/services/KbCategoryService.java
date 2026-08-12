package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.KbCategoryDto;
import com.turbotikects.turbotikectsserver.dto.SaveKbCategoryRequestDto;
import com.turbotikects.turbotikectsserver.entitys.KbCategoryEntity;
import com.turbotikects.turbotikectsserver.repositorys.KbCategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class KbCategoryService {

    private final KbCategoryRepository categoryRepo;

    public KbCategoryService(KbCategoryRepository categoryRepo) {
        this.categoryRepo = categoryRepo;
    }

    public List<KbCategoryDto> getAll() {
        return categoryRepo.findAllByOrderByDisplayOrderAsc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public KbCategoryDto create(SaveKbCategoryRequestDto dto) {
        KbCategoryEntity entity = new KbCategoryEntity();
        entity.setName(dto.getName());
        entity.setIcon(dto.getIcon());
        entity.setDisplayOrder(categoryRepo.findAllByOrderByDisplayOrderAsc().size() + 1);
        entity = categoryRepo.save(entity);
        return toDto(entity);
    }

    public KbCategoryDto update(Long id, SaveKbCategoryRequestDto dto) {
        KbCategoryEntity entity = categoryRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        entity.setName(dto.getName());
        entity.setIcon(dto.getIcon());
        entity = categoryRepo.save(entity);
        return toDto(entity);
    }

    public void delete(Long id) {
        if (!categoryRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
        }
        categoryRepo.deleteById(id);
    }

    private KbCategoryDto toDto(KbCategoryEntity e) {
        KbCategoryDto dto = new KbCategoryDto();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setIcon(e.getIcon());
        dto.setDisplayOrder(e.getDisplayOrder());
        return dto;
    }
}
