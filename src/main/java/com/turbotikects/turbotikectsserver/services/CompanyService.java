package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.entitys.CompanyEntity;
import com.turbotikects.turbotikectsserver.entitys.HoursOfOperationEntity;
import com.turbotikects.turbotikectsserver.repositorys.CompanyRepository;
import com.turbotikects.turbotikectsserver.repositorys.GroupRepository;
import com.turbotikects.turbotikectsserver.repositorys.HoursOfOperationRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CompanyService {

    @Autowired private CompanyRepository       companyRepo;
    @Autowired private HoursOfOperationRepository hooRepo;
    @Autowired private UserRepository          userRepo;
    @Autowired private GroupRepository         groupRepo;
    @Autowired private AiSettingsService       aiSettingsService;

    // ── COMPANY CRUD ──────────────────────────────────────────────────────────

    public List<CompanyDto> getAll() {
        return companyRepo.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public CompanyDto create(CreateCompanyDto dto) {
        if (dto.getName() == null || dto.getName().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company name is required");
        CompanyEntity e = new CompanyEntity();
        e.setName(dto.getName().trim());
        e.setDescription(dto.getDescription());
        e.setTimezone(dto.getTimezone() != null && !dto.getTimezone().isBlank() ? dto.getTimezone() : "UTC");
        return toDto(companyRepo.save(e));
    }

    public CompanyDto update(Integer id, CreateCompanyDto dto) {
        CompanyEntity e = companyRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        if (dto.getName() != null && !dto.getName().isBlank()) e.setName(dto.getName().trim());
        if (dto.getDescription() != null) e.setDescription(dto.getDescription());
        if (dto.getTimezone() != null && !dto.getTimezone().isBlank()) e.setTimezone(dto.getTimezone());
        return toDto(companyRepo.save(e));
    }

    public void delete(Integer id) {
        if (!companyRepo.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found");
        companyRepo.deleteById(id);
    }

    private CompanyDto toDto(CompanyEntity e) {
        CompanyDto dto = new CompanyDto();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setDescription(e.getDescription());
        dto.setTimezone(e.getTimezone());
        dto.setUserCount(userRepo.countByCompanyId(e.getId()));
        dto.setGroupCount(groupRepo.countByCompanyId(e.getId()));
        return dto;
    }

    // ── HOURS OF OPERATION ────────────────────────────────────────────────────

    public HoursOfOperationDto getHours(Integer companyId) {
        List<HoursOfOperationEntity> rows = hooRepo.findByCompanyId(companyId);
        if (rows.isEmpty()) rows = hooRepo.findByCompanyIdIsNull(); // fallback to global
        String timezone = companyId != null
                ? companyRepo.findById(companyId).map(CompanyEntity::getTimezone).orElse("UTC")
                : "UTC";
        return toHooDto(rows, timezone);
    }

    public HoursOfOperationDto getGlobalHours() {
        return toHooDto(hooRepo.findByCompanyIdIsNull(), "UTC");
    }

    @Transactional
    public void saveHours(Integer companyId, HoursOfOperationDto dto) {
        hooRepo.deleteByCompanyId(companyId);
        saveRows(companyId, dto);
        if (companyId != null && dto.getTimezone() != null) {
            companyRepo.findById(companyId).ifPresent(c -> {
                c.setTimezone(dto.getTimezone());
                companyRepo.save(c);
            });
        }
    }

    @Transactional
    public void saveGlobalHours(HoursOfOperationDto dto) {
        hooRepo.deleteGlobal();
        saveRows(null, dto);
    }

    private void saveRows(Integer companyId, HoursOfOperationDto dto) {
        if (dto.getDays() == null) return;
        for (DayScheduleDto d : dto.getDays()) {
            HoursOfOperationEntity e = new HoursOfOperationEntity();
            e.setCompanyId(companyId);
            e.setDayOfWeek(d.getDayOfWeek());
            e.setOpen(d.isOpen());
            e.setSlot1Start(d.getSlot1Start());
            e.setSlot1End(d.getSlot1End());
            e.setSlot2Start(d.getSlot2Start());
            e.setSlot2End(d.getSlot2End());
            hooRepo.save(e);
        }
    }

    private HoursOfOperationDto toHooDto(List<HoursOfOperationEntity> rows, String timezone) {
        List<DayScheduleDto> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            final int day = i;
            HoursOfOperationEntity row = rows.stream()
                    .filter(r -> r.getDayOfWeek() == day).findFirst().orElse(null);
            if (row != null) {
                days.add(new DayScheduleDto(row.getDayOfWeek(), row.isOpen(),
                        row.getSlot1Start(), row.getSlot1End(),
                        row.getSlot2Start(), row.getSlot2End()));
            } else {
                days.add(new DayScheduleDto(i, true, null, null, null, null));
            }
        }
        HoursOfOperationDto dto = new HoursOfOperationDto();
        dto.setDays(days);
        dto.setTimezone(timezone);
        return dto;
    }

    // ── AI PARSE ─────────────────────────────────────────────────────────────

    public List<DayScheduleDto> parseHoursWithAI(String text) {
        AiSettingsEntity ai = aiSettingsService.getActiveAi();
        if (ai == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No active AI provider configured. Go to Settings → AI to configure one.");

        String userPrompt = """
                Parse the following working hours description and return ONLY a valid JSON array of exactly 7 objects — one per day of the week starting from Sunday (day 0) to Saturday (day 6).
                Each object must have these exact fields:
                  "day_of_week" (integer 0=Sun…6=Sat), "is_open" (boolean),
                  "slot1_start" ("HH:mm" or null), "slot1_end" ("HH:mm" or null),
                  "slot2_start" ("HH:mm" or null — use only when there is a break),
                  "slot2_end"   ("HH:mm" or null).
                Rules:
                - If there is NO break: slot1_start=open time, slot1_end=close time, slot2_start=null, slot2_end=null.
                - If there IS a break: slot1_start=open, slot1_end=break start, slot2_start=break end, slot2_end=close.
                - Closed day: is_open=false, all times null.
                Return ONLY the JSON array, no markdown fences, no explanation.
                Description: """ + text;

        LlmStructure sys = new LlmStructure("system",
                "You are a precise JSON generator. Return only valid JSON with no extra text.");
        LlmStructure usr = new LlmStructure("user", userPrompt);

        try {
            String raw = aiSettingsService.sendLlmRequest(ai, java.util.List.of(sys, usr));
            raw = raw.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replaceAll("```", "").trim();
            return new ObjectMapper().readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI returned unparseable response: " + e.getMessage());
        }
    }
}
