package com.campusconnect.common.file;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PdfValidationTest {

    @Test
    void realPdfHeader_isPdf() {
        byte[] pdf = "%PDF-1.4\n...body...\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        assertThat(PdfValidation.isPdf(pdf)).isTrue();
    }

    @Test
    void htmlNamedAsPdf_isNotPdf() {
        byte[] html = "<!DOCTYPE html><html></html>".getBytes(StandardCharsets.US_ASCII);
        assertThat(PdfValidation.isPdf(html)).isFalse();
    }

    @Test
    void pngBytes_areNotPdf() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertThat(PdfValidation.isPdf(png)).isFalse();
    }

    @Test
    void emptyOrTooShort_isNotPdf() {
        assertThat(PdfValidation.isPdf(new byte[0])).isFalse();
        assertThat(PdfValidation.isPdf(null)).isFalse();
        assertThat(PdfValidation.isPdf("%PDF".getBytes(StandardCharsets.US_ASCII))).isFalse(); // missing trailing '-'
    }
}
