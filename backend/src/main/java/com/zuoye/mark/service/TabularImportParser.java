package com.zuoye.mark.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TabularImportParser {

    public List<Map<String, Object>> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "EMPTY_FILE",
                    "请选择批改文件。");
        }
        String filename = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                return parseWorkbook(file.getInputStream());
            }
            return parseCsv(file.getInputStream());
        } catch (PlatformException error) {
            throw error;
        } catch (Exception error) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "FILE_PARSE_ERROR",
                    "批改文件无法解析：" + error.getMessage(),
                    error);
        }
    }

    private List<Map<String, Object>> parseCsv(InputStream input)
            throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("文件没有表头。");
            }
            List<String> headers = parseCsvLine(headerLine);
            List<Map<String, Object>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                rows.add(toRow(headers, parseCsvLine(line)));
            }
            return rows;
        }
    }

    private List<Map<String, Object>> parseWorkbook(InputStream input)
            throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) throw new IllegalArgumentException("文件没有表头。");
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(formatter.formatCellValue(cell).trim());
            }
            for (int index = headerRow.getRowNum() + 1;
                    index <= sheet.getLastRowNum();
                    index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                List<String> values = new ArrayList<>();
                for (int column = 0; column < headers.size(); column++) {
                    values.add(formatter.formatCellValue(row.getCell(column)).trim());
                }
                rows.add(toRow(headers, values));
            }
        }
        return rows;
    }

    private Map<String, Object> toRow(
            List<String> headers,
            List<String> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String key = normalizeHeader(headers.get(index));
            String value = index < values.size() ? values.get(index) : "";
            if ("isCorrect".equals(key)) {
                result.put("isCorrect", parseBoolean(value));
            } else if ("score".equals(key)) {
                result.put("score", value.isBlank() ? 0 : Double.parseDouble(value));
            } else if ("seqNo".equals(key)) {
                result.put("seqNo", value.isBlank() ? 0 : Integer.parseInt(value));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private String normalizeHeader(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "学号", "studentno", "student_no" -> "studentNo";
            case "题号", "questionseq", "question_seq", "seqno", "seq_no" -> "seqNo";
            case "学生答案", "答案", "studentanswer", "student_answer" -> "studentAnswer";
            case "是否正确", "正确", "iscorrect", "is_correct" -> "isCorrect";
            case "得分", "score" -> "score";
            case "教师备注", "备注", "teachernote", "teacher_note" -> "teacherNote";
            case "错误标签", "errortag", "error_tag" -> "errorTag";
            default -> value.trim();
        };
    }

    private boolean parseBoolean(String value) {
        String normalized = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true")
                || normalized.equals("1")
                || normalized.equals("正确")
                || normalized.equals("对")
                || normalized.equals("yes");
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted
                        && index + 1 < line.length()
                        && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        values.add(current.toString().trim());
        return values;
    }
}
