package org.example.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import lombok.extern.slf4j.Slf4j;
import org.example.aiagent.constant.FileConstant;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Slf4j
public class PDFGenerationTool {

    /**
     * 中文字体。原实现每次调用都 createFont，PDF 生成是 CPU 密集型，
     * 字体对象线程安全且可复用，提升为常量避免重复解析字体文件。
     */
    private static final String CJK_FONT = "STSongStd-Light";
    private static final String CJK_ENCODING = "UniGB-UCS2-H";

    @Tool(description = "Generate a PDF file with given content")
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {

        if (StrUtil.isBlank(fileName)) {
            return "[INVALID_ARGUMENT] 文件名不能为空";
        }
        // 路径穿越防护
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return "[INVALID_ARGUMENT] 文件名不能包含路径分隔符或 ..";
        }
        if (content == null) {
            return "[INVALID_ARGUMENT] PDF 内容不能为空";
        }
        // 统一补上 .pdf 后缀，避免模型生成无后缀文件后用户找不到
        String safeName = fileName.toLowerCase().endsWith(".pdf") ? fileName : fileName + ".pdf";

        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + safeName;

        try {
            FileUtil.mkdir(fileDir);

            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {

                PdfFont font = PdfFontFactory.createFont(CJK_FONT, CJK_ENCODING);
                document.setFont(font);

                // 按换行拆分段落：整块文本直接包进一个 Paragraph 会导致长文档无法正常分页
                for (String line : content.split("\r?\n")) {
                    document.add(new Paragraph(line));
                }
            }
            return "PDF 已生成: " + filePath;
        } catch (Exception e) {
            // 原代码只捕 IOException，字体缺失等 RuntimeException 会穿透并中断整个智能体
            log.error("生成 PDF 失败, file={}", safeName, e);
            return "[IO_ERROR] 生成 PDF 失败: " + e.getMessage();
        }
    }
}
