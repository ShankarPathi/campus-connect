package com.campusconnect.student.resume;

import com.campusconnect.common.domain.Resume;
import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.file.FileStorageService;
import com.campusconnect.common.file.PdfValidation;
import com.campusconnect.common.repository.ResumeRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Upload / preview the authenticated student's own resume (Story 3.2, FR-8).
 *
 * <p>Ownership is structural: {@code userId} is always {@link TenantContext#getUserId()} and no resume-id
 * is ever accepted, so a student can only touch their own resume. A successful upload validates the file
 * by content + size, stores it privately in MinIO, and makes it the single active version (prior versions
 * are retained, immutable — an Epic-5 apply snapshots an exact {@code s3Key}).
 */
@Service
public class ResumeService {

    private static final Duration PREVIEW_TTL = Duration.ofMinutes(15);
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final ResumeRepository resumeRepository;
    private final FileStorageService fileStorage;
    private final long maxSizeBytes;

    public ResumeService(ResumeRepository resumeRepository, FileStorageService fileStorage,
                         @Value("${app.resume.max-size-bytes:5242880}") long maxSizeBytes) {
        this.resumeRepository = resumeRepository;
        this.fileStorage = fileStorage;
        this.maxSizeBytes = maxSizeBytes;
    }

    /** Validate + store a new resume, making it the student's single active version. */
    public ResumeResponse upload(MultipartFile file) {
        byte[] bytes = readNonEmpty(file);
        if (!PdfValidation.isPdf(bytes)) {
            throw new BusinessException(ErrorCode.RESUME_INVALID_TYPE, "Only PDF resumes are accepted.");
        }
        if (bytes.length > maxSizeBytes) {
            throw new BusinessException(ErrorCode.RESUME_TOO_LARGE,
                    "The resume exceeds the maximum allowed size.");
        }

        String userId = currentUserId();
        String tenantId = TenantContext.requireTenantId();
        int version = resumeRepository.nextVersionFor(userId);
        String s3Key = "resumes/%s/%s/%d-%s.pdf".formatted(tenantId, userId, version, UUID.randomUUID());

        fileStorage.put(s3Key, bytes, PDF_CONTENT_TYPE);

        // Deactivate the prior active version (kept, not deleted), then insert the new active one.
        resumeRepository.findActiveByUserId(userId).ifPresent(prev -> {
            prev.setActive(false);
            resumeRepository.save(prev);
        });

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setS3Key(s3Key);
        resume.setOriginalName(file.getOriginalFilename());
        resume.setMimeType(PDF_CONTENT_TYPE);
        resume.setVersion(version);
        resume.setActive(true);
        resume.setSizeBytes(bytes.length);
        Resume saved = resumeRepository.save(resume);

        return ResumeResponse.of(saved, presign(saved), (int) PREVIEW_TTL.toSeconds());
    }

    /** The student's active resume + a fresh 15-minute preview URL, or an empty view if none. */
    public ResumeResponse getMyResume() {
        return resumeRepository.findActiveByUserId(currentUserId())
                .map(r -> ResumeResponse.of(r, presign(r), (int) PREVIEW_TTL.toSeconds()))
                .orElseGet(ResumeResponse::none);
    }

    // ── internals ──

    private String currentUserId() {
        return TenantContext.getUserId();
    }

    private String presign(Resume resume) {
        return fileStorage.presignedGetUrl(resume.getS3Key(), PREVIEW_TTL);
    }

    private byte[] readNonEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "No file was uploaded.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The uploaded file could not be read.");
        }
    }
}
