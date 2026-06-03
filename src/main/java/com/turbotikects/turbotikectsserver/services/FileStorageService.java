package com.turbotikects.turbotikectsserver.services;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorageService {
    void store(InputStream data, String relativePath, long size) throws IOException;
    byte[] retrieve(String relativePath) throws IOException;
    byte[] retrieveRange(String relativePath, long start, long end) throws IOException;
    long getSize(String relativePath) throws IOException;
    void delete(String relativePath) throws IOException;
    boolean exists(String relativePath);
}
