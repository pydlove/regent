package com.pydlove.regent.shared.kernel;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentUploadPolicyTest {

    @Test
    @DisplayName("Markdown 文档按扩展名确定类型并计算内容摘要")
    void validatesMarkdown() {
        byte[] content = "# 需求\n详细说明".getBytes(StandardCharsets.UTF_8);

        var document = DocumentUploadPolicy.validate(
                "../资料/需求.md", content.length, content, 1024);

        assertEquals("需求.md", document.filename());
        assertEquals("text/markdown", document.contentType());
        assertEquals(64, document.contentHash().length());
    }

    @Test
    @DisplayName("伪造扩展名的 PDF 和 Office 文档被拒绝")
    void rejectsInvalidMagic() {
        byte[] text = "not a pdf".getBytes(StandardCharsets.UTF_8);
        assertThrows(DomainException.class, () ->
                DocumentUploadPolicy.validate("需求.pdf", text.length, text, 1024));
        assertThrows(DomainException.class, () ->
                DocumentUploadPolicy.validate("需求.docx", text.length, text, 1024));
    }

    @Test
    @DisplayName("现代 Office 文档必须是 ZIP 容器")
    void acceptsOfficeZip() {
        byte[] content = new byte[]{'P', 'K', 3, 4, 0, 0};

        var document = DocumentUploadPolicy.validate(
                "方案.docx", content.length, content, 1024);

        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                document.contentType());
    }

    @Test
    @DisplayName("HTML 与伪造文本被拒绝")
    void rejectsUnsafeOrBinaryContent() {
        byte[] html = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertThrows(DomainException.class, () ->
                DocumentUploadPolicy.validate("说明.html", html.length, html, 1024));

        byte[] binaryText = new byte[]{'a', 0, 'b'};
        assertThrows(DomainException.class, () ->
                DocumentUploadPolicy.validate("说明.txt", binaryText.length, binaryText, 1024));
    }
}
