package com.muse.muaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.muse.muaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.util.stream.Collectors;

public class PDFGenerationTool {

    // Windows 系统微软雅黑字体路径
    private static final String FONT_PATH = "C:/Windows/Fonts/msyh.ttc,0";

    @Tool(description = "Generate a PDF file with given content")
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        try {
            FileUtil.mkdir(fileDir);
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                PdfFont font = PdfFontFactory.createFont(FONT_PATH,
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                document.setFont(font);
                // 移除 emoji 避免字体不支持报错
                Paragraph paragraph = new Paragraph(removeEmoji(content));
                document.add(paragraph);
            }
            return "PDF generated successfully to: " + filePath;
        } catch (IOException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }

    /**
     * 移除字符串中超出 BMP（基本多语言平面）的字符，包括 emoji
     */
    private String removeEmoji(String text) {
        if (text == null) return null;
        return text.codePoints()
                .filter(cp -> cp < 0x10000)
                .mapToObj(Character::toString)
                .collect(Collectors.joining());
    }
}

