package com.turbotikects.turbotikectsserver.services;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@Primary
public class LocalFileStorageService implements FileStorageService {

    @Value("${app.attachments.storage-path}")
    private String storagePath;

    private Path storageRoot;

    @PostConstruct
    public void init() throws IOException {
        storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
        Files.createDirectories(storageRoot);
    }

    private Path resolve(String relativePath) {
        Path resolved = storageRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new SecurityException("Path traversal attempt detected");
        }
        return resolved;
    }

    @Override
    public void store(InputStream data, String relativePath, long size) throws IOException {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public byte[] retrieve(String relativePath) throws IOException {
        return Files.readAllBytes(resolve(relativePath));
    }

    @Override
    public byte[] retrieveRange(String relativePath, long start, long end) throws IOException {
        Path target = resolve(relativePath);
        int length = (int) (end - start + 1);
        byte[] buffer = new byte[length];
        try (RandomAccessFile raf = new RandomAccessFile(target.toFile(), "r")) {
            raf.seek(start);
            raf.readFully(buffer);
        }
        return buffer;
    }

    @Override
    public long getSize(String relativePath) throws IOException {
        return Files.size(resolve(relativePath));
    }

    @Override
    public void delete(String relativePath) throws IOException {
        Files.deleteIfExists(resolve(relativePath));
    }

    @Override
    public boolean exists(String relativePath) {
        return Files.exists(resolve(relativePath));
    }
}
