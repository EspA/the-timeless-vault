package com.thetimelessvault.storage;

import com.thetimelessvault.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalObjectStorage implements ObjectStorage {

    private final Path root;
    private final AppProperties properties;

    public LocalObjectStorage(AppProperties properties) {
        this.properties = properties;
        this.root = Path.of(properties.getStorage().getLocalDir()).toAbsolutePath();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String store(String key, InputStream content, String contentType, long size) {
        try {
            Path target = root.resolve(key);
            Files.createDirectories(target.getParent());
            Files.copy(content, target);
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream read(String key) {
        try {
            return Files.newInputStream(root.resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String publicUrl(String key) {
        return properties.getBaseUrl() + "/api/photos/file/" + key;
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(root.resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String mode() {
        return "local";
    }
}
