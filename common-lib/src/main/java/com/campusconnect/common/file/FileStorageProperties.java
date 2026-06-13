package com.campusconnect.common.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object-storage connection settings (Story 3.2), bound from {@code app.storage.*}. In dev these point
 * at the Docker-Compose MinIO ({@code http://localhost:9000}, {@code minioadmin}/{@code minioadmin}); in
 * prod they come from environment variables (Epic 10). The bucket is private — access is only ever via
 * short-lived pre-signed URLs.
 */
@ConfigurationProperties("app.storage")
public class FileStorageProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket = "campus-connect";

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }
}
