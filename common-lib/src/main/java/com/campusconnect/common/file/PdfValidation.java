package com.campusconnect.common.file;

/**
 * Backend PDF detection by content (Story 3.2, FR-8 / NFR-2). Validates the file's leading bytes against
 * the PDF magic number {@code %PDF-} — NOT the filename extension or the client-supplied
 * {@code Content-Type}, both of which are trivially spoofable. A 5-byte check is sufficient and pulls in
 * no dependency (Apache Tika would be overkill for one signature).
 */
public final class PdfValidation {

    /** {@code %PDF-} — the mandatory leading bytes of every PDF document. */
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46, 0x2D};

    private PdfValidation() {
    }

    /** True iff {@code bytes} begins with the {@code %PDF-} magic number. */
    public static boolean isPdf(byte[] bytes) {
        if (bytes == null || bytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
