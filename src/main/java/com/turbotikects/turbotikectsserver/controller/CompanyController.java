package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.services.CompanyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    @Autowired private CompanyService companyService;

    private void requireSuperAdmin(HttpServletRequest req) {
        UserDto caller = (UserDto) req.getAttribute("currentUser");
        if (caller == null || !caller.isSuperAdmin())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super admin only");
    }

    @GetMapping
    public List<CompanyDto> getAll(HttpServletRequest req) {
        requireSuperAdmin(req);
        return companyService.getAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyDto create(@RequestBody CreateCompanyDto dto, HttpServletRequest req) {
        requireSuperAdmin(req);
        return companyService.create(dto);
    }

    @PatchMapping("/{id}")
    public CompanyDto update(@PathVariable Integer id, @RequestBody CreateCompanyDto dto,
                             HttpServletRequest req) {
        requireSuperAdmin(req);
        return companyService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id, HttpServletRequest req) {
        requireSuperAdmin(req);
        companyService.delete(id);
    }

    @GetMapping("/{id}/hours")
    public HoursOfOperationDto getHours(@PathVariable Integer id, HttpServletRequest req) {
        requireSuperAdmin(req);
        return companyService.getHours(id);
    }

    @PutMapping("/{id}/hours")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveHours(@PathVariable Integer id, @RequestBody HoursOfOperationDto dto,
                          HttpServletRequest req) {
        requireSuperAdmin(req);
        companyService.saveHours(id, dto);
    }

    @GetMapping("/global/hours")
    public HoursOfOperationDto getGlobalHours(HttpServletRequest req) {
        requireSuperAdmin(req);
        return companyService.getGlobalHours();
    }

    @PutMapping("/global/hours")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveGlobalHours(@RequestBody HoursOfOperationDto dto, HttpServletRequest req) {
        requireSuperAdmin(req);
        companyService.saveGlobalHours(dto);
    }

    @PostMapping("/hours/parse-ai")
    public List<DayScheduleDto> parseAi(@RequestBody Map<String, String> body,
                                        HttpServletRequest req) {
        requireSuperAdmin(req);
        String text = body.getOrDefault("text", "");
        if (text.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
        return companyService.parseHoursWithAI(text);
    }
}
