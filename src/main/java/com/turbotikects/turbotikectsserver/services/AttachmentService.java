package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.AttachmentDto;
import com.turbotikects.turbotikectsserver.dto.AttachmentTokenResponseDto;
import com.turbotikects.turbotikectsserver.entitys.AttachmentEntity;
import com.turbotikects.turbotikectsserver.entitys.AttachmentFileEntity;
import com.turbotikects.turbotikectsserver.repositorys.AttachmentFileRepository;
import com.turbotikects.turbotikectsserver.repositorys.AttachmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class AttachmentService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final AttachmentRepository attachmentRepo;
    private final AttachmentFileRepository attachmentFileRepo;
    private final FileStorageService fileStorage;
    private final AttachmentTokenStore tokenStore;

    public AttachmentService(AttachmentRepository attachmentRepo,
                             AttachmentFileRepository attachmentFileRepo,
                             FileStorageService fileStorage,
                             AttachmentTokenStore tokenStore) {
        this.attachmentRepo = attachmentRepo;
        this.attachmentFileRepo = attachmentFileRepo;
        this.fileStorage = fileStorage;
        this.tokenStore = tokenStore;
    }

    public List<AttachmentDto> upload(String entityType, Long entityId,
                                      MultipartFile[] files, Long uploadedBy) throws IOException {
        List<AttachmentDto> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "File " + file.getOriginalFilename() + " exceeds 5 MB limit");
            }

            byte[] bytes = file.getBytes();
            String hash = sha256(bytes);
            String ext = extractExtension(file.getOriginalFilename());
            String storedFilename = hash.substring(0, 2) + "/" + hash + (ext.isEmpty() ? "" : "." + ext);
            String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

            AttachmentFileEntity fileEntity = attachmentFileRepo.findByContentHash(hash).orElse(null);
            if (fileEntity == null) {
                fileStorage.store(new ByteArrayInputStream(bytes), storedFilename, bytes.length);
                fileEntity = new AttachmentFileEntity();
                fileEntity.setContentHash(hash);
                fileEntity.setStoredFilename(storedFilename);
                fileEntity.setMimeType(mimeType);
                fileEntity.setFileSize((long) bytes.length);
                fileEntity.setRefCount(1);
                fileEntity = attachmentFileRepo.save(fileEntity);
            } else {
                fileEntity.setRefCount(fileEntity.getRefCount() + 1);
                fileEntity = attachmentFileRepo.save(fileEntity);
            }

            AttachmentEntity attachment = new AttachmentEntity();
            attachment.setEntityType(entityType);
            attachment.setEntityId(entityId);
            attachment.setOriginalFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
            attachment.setFileId(fileEntity.getId());
            attachment.setUploadedBy(uploadedBy);
            attachment = attachmentRepo.save(attachment);

            results.add(toDto(attachment, fileEntity));
        }
        return results;
    }

    public List<AttachmentDto> list(String entityType, Long entityId) {
        List<AttachmentEntity> attachments =
                attachmentRepo.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
        List<AttachmentDto> dtos = new ArrayList<>();
        for (AttachmentEntity a : attachments) {
            attachmentFileRepo.findById(a.getFileId()).ifPresent(f -> dtos.add(toDto(a, f)));
        }
        return dtos;
    }

    public void delete(List<Long> ids) throws IOException {
        for (Long id : ids) {
            AttachmentEntity attachment = attachmentRepo.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            AttachmentFileEntity fileEntity = attachmentFileRepo.findById(attachment.getFileId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

            attachmentRepo.delete(attachment);

            int newCount = fileEntity.getRefCount() - 1;
            if (newCount <= 0) {
                fileStorage.delete(fileEntity.getStoredFilename());
                attachmentFileRepo.delete(fileEntity);
            } else {
                fileEntity.setRefCount(newCount);
                attachmentFileRepo.save(fileEntity);
            }
        }
    }

    public AttachmentDto duplicate(Long id) {
        AttachmentEntity source = attachmentRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        AttachmentFileEntity fileEntity = attachmentFileRepo.findById(source.getFileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        fileEntity.setRefCount(fileEntity.getRefCount() + 1);
        fileEntity = attachmentFileRepo.save(fileEntity);

        AttachmentEntity copy = new AttachmentEntity();
        copy.setEntityType(source.getEntityType());
        copy.setEntityId(source.getEntityId());
        copy.setOriginalFilename(source.getOriginalFilename());
        copy.setFileId(fileEntity.getId());
        copy.setUploadedBy(source.getUploadedBy());
        copy = attachmentRepo.save(copy);

        return toDto(copy, fileEntity);
    }

    public AttachmentTokenResponseDto issueToken(Long id) {
        attachmentRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String token = tokenStore.issue(id);
        AttachmentTokenResponseDto dto = new AttachmentTokenResponseDto();
        dto.setToken(token);
        dto.setExpiresAt(Instant.now().plusSeconds(3600));
        return dto;
    }

    public record ResolvedAttachment(AttachmentEntity attachment, AttachmentFileEntity fileEntity) {}

    public ResolvedAttachment resolveToken(String token) {
        Optional<Long> attachmentId = tokenStore.validate(token);
        if (attachmentId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or expired token");
        }
        AttachmentEntity attachment = attachmentRepo.findById(attachmentId.get())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        AttachmentFileEntity fileEntity = attachmentFileRepo.findById(attachment.getFileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new ResolvedAttachment(attachment, fileEntity);
    }

    private AttachmentDto toDto(AttachmentEntity a, AttachmentFileEntity f) {
        AttachmentDto dto = new AttachmentDto();
        dto.setId(a.getId());
        dto.setEntityType(a.getEntityType());
        dto.setEntityId(a.getEntityId());
        dto.setOriginalFilename(a.getOriginalFilename());
        dto.setMimeType(f.getMimeType());
        dto.setFileSize(f.getFileSize());
        dto.setUploadedBy(a.getUploadedBy());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1) ? filename.substring(dot + 1) : "";
    }
}
