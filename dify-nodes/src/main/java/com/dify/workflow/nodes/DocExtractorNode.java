package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * DocExtractor node - 文档提取器。
 * 从 PDF/Word/Excel/TXT 文件中提取纯文本。
 * 参考 Dify 的 DocumentExtractorNode (graphon.nodes.document_extractor)。
 *
 * 文件来源: 本地路径 或 URL
 * 支持格式: .txt .csv .json .xml .md .pdf .docx .xlsx
 */
public class DocExtractorNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(DocExtractorNode.class);
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public DocExtractorNode(String id, DifyNodeData data) {
        super(id, NodeType.DOC_EXTRACTOR, data);
    }

    @Override
    public void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing doc extractor node: {}", id);

        // 读取文件变量: variable_selector → [nodeId, varName]
        List<String> varSelector = null;
        if (data.variables() != null && !data.variables().isEmpty()) {
            varSelector = data.variables().get(0).valueSelector();
        }

        Object fileObj = null;
        if (varSelector != null && varSelector.size() >= 2) {
            fileObj = context.getVariable(varSelector.get(0), varSelector.get(1));
        }

        if (fileObj == null) {
            log.warn("Doc extractor: no file input found");
            context.setVariable(id, "text", "");
            return;
        }

        // 支持单文件(String/Map) 或 多文件(List)
        List<String> filePaths = new ArrayList<>();
        if (fileObj instanceof List) {
            for (Object item : (List<?>) fileObj) {
                filePaths.add(resolveFileObject(item));
            }
        } else {
            filePaths.add(resolveFileObject(fileObj));
        }

        List<String> results = new ArrayList<>();
        for (String filePath : filePaths) {
            results.add(extractText(filePath));
        }

        if (results.size() == 1) {
            context.setVariable(id, "text", results.get(0));
        } else {
            context.setVariable(id, "text", results);
        }

        log.debug("Doc extractor result: {} files", results.size());
    }

    private String extractText(String filePath) throws Exception {
        File file = resolveFile(filePath);
        String name = file.getName().toLowerCase();

        try (InputStream in = new FileInputStream(file)) {
            if (name.endsWith(".pdf")) {
                return extractPdf(file);
            } else if (name.endsWith(".docx")) {
                return extractDocx(file);
            } else if (name.endsWith(".xlsx")) {
                return extractXlsx(file);
            } else if (isPlainText(name)) {
                return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            } else {
                throw new IllegalArgumentException("Unsupported file type: " + name);
            }
        }
    }

    /**
     * 解析文件对象，支持 Dify 文件格式和纯字符串路径。
     *
     * Dify 文件格式: {type: "file", transfer_method: "remote_url|local_file", url: "...", name: "..."}
     */
    @SuppressWarnings("unchecked")
    private String resolveFileObject(Object fileObj) {
        if (fileObj instanceof Map) {
            Map<String, Object> fileMap = (Map<String, Object>) fileObj;
            String transferMethod = (String) fileMap.getOrDefault("transfer_method", "local_file");

            if ("remote_url".equals(transferMethod)) {
                // 远程URL: 直接返回URL
                String url = (String) fileMap.get("url");
                if (url != null && !url.isEmpty()) {
                    return url;
                }
            }

            // local_file: 尝试从 upload_file_id 或 url 获取
            String uploadFileId = (String) fileMap.get("upload_file_id");
            if (uploadFileId != null && !uploadFileId.isEmpty()) {
                return uploadFileId;
            }
            String url = (String) fileMap.get("url");
            if (url != null && !url.isEmpty()) {
                return url;
            }

            // 兜底: 使用文件名
            String name = (String) fileMap.get("name");
            if (name != null) {
                return name;
            }
            return "";
        }
        return String.valueOf(fileObj);
    }

    /** 解析文件来源: 本地路径 或 URL */
    private File resolveFile(String path) throws IOException {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            // URL → 下载到临时文件
            String ext = "";
            int dotIdx = path.lastIndexOf('.');
            if (dotIdx > 0) ext = path.substring(dotIdx);

            Path tmp = Files.createTempFile("doc_extract_", ext);
            okhttp3.Response resp = httpClient.newCall(
                    new Request.Builder().url(path).build()).execute();
            try {
                okhttp3.ResponseBody body = resp.body();
                if (body == null) throw new IOException("Empty response from: " + path);
                Files.copy(body.byteStream(), tmp, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                resp.close();
            }
            return tmp.toFile();
        }
        // 本地路径
        return new File(path);
    }

    private boolean isPlainText(String name) {
        return name.endsWith(".txt") || name.endsWith(".csv") || name.endsWith(".json")
                || name.endsWith(".xml") || name.endsWith(".md") || name.endsWith(".html")
                || name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".log");
    }

    /* ---- PDF (PDFBox) ---- */
    private String extractPdf(File file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    /* ---- Word (POI XWPF) ---- */
    private String extractDocx(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {
            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
            return sb.toString();
        }
    }

    /* ---- Excel (POI XSSF) ---- */
    private String extractXlsx(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {
            StringBuilder sb = new StringBuilder();
            wb.forEach(sheet -> {
                sb.append("[").append(sheet.getSheetName()).append("]\n");
                sheet.forEach(row -> {
                    row.forEach(cell -> {
                        switch (cell.getCellType()) {
                            case STRING: sb.append(cell.getStringCellValue()); break;
                            case NUMERIC: sb.append(cell.getNumericCellValue()); break;
                            case BOOLEAN: sb.append(cell.getBooleanCellValue()); break;
                            default: break;
                        }
                        sb.append("\t");
                    });
                    sb.append("\n");
                });
            });
            return sb.toString();
        }
    }
}
