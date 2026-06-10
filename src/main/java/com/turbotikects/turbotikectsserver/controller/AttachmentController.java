package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.AttachmentDto;
import com.turbotikects.turbotikectsserver.dto.AttachmentTokenResponseDto;
import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.entitys.AttachmentEntity;
import com.turbotikects.turbotikectsserver.entitys.AttachmentFileEntity;
import com.turbotikects.turbotikectsserver.services.AttachmentService;
import com.turbotikects.turbotikectsserver.services.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final FileStorageService fileStorage;

    public AttachmentController(AttachmentService attachmentService, FileStorageService fileStorage) {
        this.attachmentService = attachmentService;
        this.fileStorage = fileStorage;
    }

    @GetMapping
    public List<AttachmentDto> list(@RequestParam String entityType, @RequestParam Long entityId) {
        return attachmentService.list(entityType, entityId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<AttachmentDto> upload(@RequestParam String entityType,
                                      @RequestParam Long entityId,
                                      @RequestParam("files") MultipartFile[] files,
                                      HttpServletRequest request) throws IOException {
        Integer uploadedBy = currentUserId(request);
        return attachmentService.upload(entityType, entityId, files, uploadedBy);
    }

    @DeleteMapping
    public void delete(@RequestBody List<Long> ids) throws IOException {
        attachmentService.delete(ids);
    }

    @PostMapping("/{id}/duplicate")
    public AttachmentDto duplicate(@PathVariable Long id) {
        return attachmentService.duplicate(id);
    }

    @GetMapping("/{id}/token")
    public AttachmentTokenResponseDto issueToken(@PathVariable Long id) {
        return attachmentService.issueToken(id);
    }

    @GetMapping("/{id}/inline")
    public ResponseEntity<ByteArrayResource> inline(@PathVariable Long id) throws IOException {
        AttachmentService.ResolvedAttachment resolved = attachmentService.loadById(id);
        AttachmentFileEntity fileEntity = resolved.fileEntity();
        byte[] data = fileStorage.retrieve(fileEntity.getStoredFilename());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + sanitizeFilename(resolved.attachment().getOriginalFilename()) + "\"")
                .contentType(MediaType.parseMediaType(fileEntity.getMimeType()))
                .contentLength(data.length)
                .body(new ByteArrayResource(data));
    }

    @GetMapping("/download")
    public ResponseEntity<ByteArrayResource> download(@RequestParam String token) throws IOException {
        AttachmentService.ResolvedAttachment resolved = attachmentService.resolveToken(token);
        AttachmentEntity attachment = resolved.attachment();
        AttachmentFileEntity fileEntity = resolved.fileEntity();

        byte[] data = fileStorage.retrieve(fileEntity.getStoredFilename());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + sanitizeFilename(attachment.getOriginalFilename()) + "\"")
                .contentType(MediaType.parseMediaType(fileEntity.getMimeType()))
                .contentLength(data.length)
                .body(new ByteArrayResource(data));
    }

    @GetMapping("/preview")
    public ResponseEntity<ByteArrayResource> preview(@RequestParam String token) throws IOException {
        AttachmentService.ResolvedAttachment resolved = attachmentService.resolveToken(token);
        AttachmentEntity attachment = resolved.attachment();
        AttachmentFileEntity fileEntity = resolved.fileEntity();

        byte[] data = fileStorage.retrieve(fileEntity.getStoredFilename());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + sanitizeFilename(attachment.getOriginalFilename()) + "\"")
                .contentType(MediaType.parseMediaType(fileEntity.getMimeType()))
                .contentLength(data.length)
                .body(new ByteArrayResource(data));
    }

    @GetMapping("/stream")
    public ResponseEntity<ByteArrayResource> stream(@RequestParam String token,
                                                    @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader)
            throws IOException {
        AttachmentService.ResolvedAttachment resolved = attachmentService.resolveToken(token);
        AttachmentFileEntity fileEntity = resolved.fileEntity();

        String path = fileEntity.getStoredFilename();
        long total = fileStorage.getSize(path);

        long start = 0;
        long end = total - 1;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] parts = rangeHeader.substring(6).split("-");
            try {
                start = Long.parseLong(parts[0]);
                end = (parts.length > 1 && !parts[1].isEmpty()) ? Long.parseLong(parts[1]) : total - 1;
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            }
        }

        end = Math.min(end, total - 1);
        byte[] chunk = fileStorage.retrieveRange(path, start, end);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + total)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(MediaType.parseMediaType(fileEntity.getMimeType()))
                .contentLength(chunk.length)
                .body(new ByteArrayResource(chunk));
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto && dto.getUserId() != null) return dto.getUserId().intValue();
        return null;
    }

    private String sanitizeFilename(String filename) {
        return filename == null ? "file" : filename.replace("\"", "").replace("\\", "");
    }
}
