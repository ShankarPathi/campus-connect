package com.campusconnect.common.file;

import java.time.Duration;

/**
 * Object storage abstraction (Story 3.2). The MVP implementation is {@link MinioFileStorageService}
 * (AWS SDK v2 against a private MinIO bucket); the interface keeps callers off the SDK and makes the
 * backend swappable (S3 in prod) behind one seam.
 */
public interface FileStorageService {

    /** Stores {@code bytes} at {@code key} with the given {@code contentType}, overwriting that key. */
    void put(String key, byte[] bytes, String contentType);

    /** A time-limited pre-signed GET URL for {@code key}, valid for {@code ttl}. Generated on demand, never stored. */
    String presignedGetUrl(String key, Duration ttl);
}
