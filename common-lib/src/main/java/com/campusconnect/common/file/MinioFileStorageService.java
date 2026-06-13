package com.campusconnect.common.file;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

/**
 * MinIO-backed {@link FileStorageService} (Story 3.2) using the AWS SDK v2 S3 client. Created only when
 * {@code app.storage.endpoint} is set (mirrors {@code SmtpEmailService}'s gating), so services without
 * storage config never instantiate it. {@code forcePathStyle} is required for MinIO (no virtual-host
 * buckets); the bucket is kept private.
 *
 * <p>The constructor does <b>no network I/O</b> — the bucket is ensured <i>lazily</i> on the first
 * {@link #put} (not at bean construction), so booting student-service in CI without a live MinIO (every
 * {@code @SpringBootTest} that doesn't upload) never fails. The S3 clients are closed on shutdown.
 */
@Service
@ConditionalOnProperty(prefix = "app.storage", name = "endpoint")
@EnableConfigurationProperties(FileStorageProperties.class)
public class MinioFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioFileStorageService.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private volatile boolean bucketReady = false;

    public MinioFileStorageService(FileStorageProperties properties) {
        this.bucket = properties.getBucket();
        URI endpoint = URI.create(properties.getEndpoint());
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
        S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        this.s3 = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1) // MinIO ignores region; a value is required by the SDK
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
        // No network I/O here — the bucket is ensured lazily on the first put (see ensureBucketOnce).
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        ensureBucketOnce();
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
    }

    @Override
    public String presignedGetUrl(String key, Duration ttl) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key).build();
        GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(get)
                .build();
        return presigner.presignGetObject(presign).url().toString();
    }

    /** Ensures the bucket exists exactly once, on the first upload (cheap double-checked guard). */
    private void ensureBucketOnce() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            ensureBucket();
            bucketReady = true;
        }
    }

    private void ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            createBucketTolerant();
        } catch (S3Exception e) {
            // A bodiless HeadBucket 404 often surfaces as a generic S3Exception, not NoSuchBucketException.
            if (e.statusCode() == 404) {
                createBucketTolerant();
            } else {
                throw e;
            }
        }
    }

    private void createBucketTolerant() {
        try {
            log.info("Creating object-storage bucket '{}'", bucket);
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException alreadyThere) {
            // Created concurrently (or by another instance) — fine.
        }
    }

    @PreDestroy
    public void close() {
        s3.close();
        presigner.close();
    }
}
