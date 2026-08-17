package com.thetimelessvault.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.thetimelessvault.config.AppProperties;
import java.io.InputStream;
import java.nio.channels.Channels;

public class GcsObjectStorage implements ObjectStorage {

    private final Storage storage;
    private final String bucket;

    public GcsObjectStorage(AppProperties properties) {
        AppProperties.Storage cfg = properties.getStorage();
        StorageOptions.Builder builder = StorageOptions.newBuilder();
        if (cfg.getGcsProjectId() != null && !cfg.getGcsProjectId().isBlank()) {
            builder.setProjectId(cfg.getGcsProjectId());
        }
        this.storage = builder.build().getService();
        this.bucket = cfg.getGcsBucket();
    }

    @Override
    public String store(String key, InputStream content, String contentType, long size) {
        try {
            BlobInfo info = BlobInfo.newBuilder(BlobId.of(bucket, key))
                    .setContentType(contentType)
                    .build();
            storage.createFrom(info, content);
            return key;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not store photo in GCS", e);
        }
    }

    @Override
    public InputStream read(String key) {
        return Channels.newInputStream(storage.reader(BlobId.of(bucket, key)));
    }

    @Override
    public String publicUrl(String key) {
        return "https://storage.googleapis.com/" + bucket + "/" + key;
    }

    @Override
    public void delete(String key) {
        storage.delete(BlobId.of(bucket, key));
    }

    @Override
    public String mode() {
        return "gcs";
    }
}
