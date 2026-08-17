package com.thetimelessvault.storage;

import java.io.InputStream;

public interface ObjectStorage {
    String store(String key, InputStream content, String contentType, long size);

    InputStream read(String key);

    String publicUrl(String key);

    void delete(String key);

    String mode();
}
