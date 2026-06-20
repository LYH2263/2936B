package com.exam.service;

import com.exam.entity.LearningAlert;
import com.exam.entity.User;
import com.exam.repository.LearningAlertRepository;
import com.exam.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class LearningAlertQueryService {

    private final LearningAlertRepository alertRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public LearningAlertQueryService(LearningAlertRepository alertRepository,
                                      UserRepository userRepository,
                                      ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public Page<LearningAlert> getAlerts(Long studentId, String alertType, String severity,
                                          Boolean isResolved, Pageable pageable) {
        return alertRepository.findByFilters(studentId, alertType, severity, isResolved, pageable);
    }

    public Page<LearningAlert> getUnresolvedAlerts(Pageable pageable) {
        return alertRepository.findByIsResolvedOrderBySeverityAscCreatedAtDesc(false, pageable);
    }

    public Map<String, Object> getAlertStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUnresolved", alertRepository.countUnresolved());

        Map<String, Long> bySeverity = new LinkedHashMap<>();
        bySeverity.put("HIGH", 0L);
        bySeverity.put("MEDIUM", 0L);
        bySeverity.put("LOW", 0L);
        for (Object[] row : alertRepository.countBySeverityUnresolved()) {
            bySeverity.put((String) row[0], ((Number) row[1]).longValue());
        }
        stats.put("bySeverity", bySeverity);

        Map<String, Long> byType = new LinkedHashMap<>();
        byType.put("CONSECUTIVE_LOW_SCORE", 0L);
        byType.put("KNOWLEDGE_POINT_LOW", 0L);
        byType.put("LONG_TIME_NO_EXAM", 0L);
        for (Object[] row : alertRepository.countByTypeUnresolved()) {
            byType.put((String) row[0], ((Number) row[1]).longValue());
        }
        stats.put("byType", byType);

        return stats;
    }

    public List<LearningAlert> getStudentOwnAlerts(Long studentId) {
        return alertRepository.findUnresolvedByStudent(studentId);
    }

    @Transactional
    public void markAsResolved(Long alertId, Principal principal) {
        User handler = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        alertRepository.resolveAlert(alertId, LocalDateTime.now(), handler);
    }

    @Transactional
    public int markBatchAsResolved(List<Long> alertIds, Principal principal) {
        User handler = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        int count = 0;
        for (Long id : alertIds) {
            alertRepository.resolveAlert(id, LocalDateTime.now(), handler);
            count++;
        }
        return count;
    }

    public Map<String, Object> getAlertDetail(Long alertId) {
        LearningAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        return enrichAlert(alert);
    }

    public byte[] exportToExcel(Long studentId, String alertType, String severity, Boolean isResolved) {
        List<LearningAlert> allAlerts = new ArrayList<>();
        Pageable pageable = Pageable.ofSize(1000);
        Page<LearningAlert> page;
        int pageNum = 0;
        do {
            page = alertRepository.findByFilters(studentId, alertType, severity, isResolved, pageable.withPage(pageNum++));
            allAlerts.addAll(page.getContent());
        } while (page.hasNext());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("学情预警");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle redStyle = createSeverityStyle(workbook, IndexedColors.RED);
            CellStyle orangeStyle = createSeverityStyle(workbook, IndexedColors.ORANGE);
            CellStyle yellowStyle = createSeverityStyle(workbook, IndexedColors.YELLOW);

            String[] headers = {"ID", "严重程度", "预警类型", "学生姓名", "学生账号", "班级",
                    "标题", "详情", "状态", "创建时间", "处理时间", "处理人"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;
            for (LearningAlert alert : allAlerts) {
                Row row = sheet.createRow(rowNum++);
                User s = alert.getStudent();

                CellStyle severityStyle = "HIGH".equals(alert.getSeverity()) ? redStyle :
                        ("MEDIUM".equals(alert.getSeverity()) ? orangeStyle : yellowStyle);

                row.createCell(0).setCellValue(alert.getId() != null ? alert.getId() : 0);

                Cell sevCell = row.createCell(1);
                sevCell.setCellValue(getSeverityText(alert.getSeverity()));
                sevCell.setCellStyle(severityStyle);

                row.createCell(2).setCellValue(getTypeText(alert.getAlertType()));
                row.createCell(3).setCellValue(s != null && s.getFullName() != null ? s.getFullName() : "");
                row.createCell(4).setCellValue(s != null && s.getUsername() != null ? s.getUsername() : "");
                row.createCell(5).setCellValue(s != null && s.getClazz() != null ? s.getClazz() : "");
                row.createCell(6).setCellValue(alert.getTitle() != null ? alert.getTitle() : "");
                row.createCell(7).setCellValue(alert.getDetail() != null ? alert.getDetail() : "");
                row.createCell(8).setCellValue(Boolean.TRUE.equals(alert.getIsResolved()) ? "已处理" : "未处理");
                row.createCell(9).setCellValue(alert.getCreatedAt() != null ? alert.getCreatedAt().format(dtf) : "");
                row.createCell(10).setCellValue(alert.getResolvedAt() != null ? alert.getResolvedAt().format(dtf) : "");
                User rb = alert.getResolvedBy();
                row.createCell(11).setCellValue(rb != null && rb.getFullName() != null ? rb.getFullName() :
                        (rb != null && rb.getUsername() != null ? rb.getUsername() : ""));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("导出 Excel 失败: " + e.getMessage(), e);
        }
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createSeverityStyle(Workbook wb, IndexedColors color) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    public Map<String, Object> enrichAlert(LearningAlert alert) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", alert.getId());
        result.put("alertType", alert.getAlertType());
        result.put("severity", alert.getSeverity());
        result.put("title", alert.getTitle());
        result.put("detail", alert.getDetail());
        result.put("isResolved", alert.getIsResolved());
        result.put("createdAt", alert.getCreatedAt());
        result.put("resolvedAt", alert.getResolvedAt());

        if (alert.getStudent() != null) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", alert.getStudent().getId());
            s.put("username", alert.getStudent().getUsername());
            s.put("fullName", alert.getStudent().getFullName());
            s.put("clazz", alert.getStudent().getClazz());
            result.put("student", s);
        }
        if (alert.getResolvedBy() != null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", alert.getResolvedBy().getId());
            r.put("fullName", alert.getResolvedBy().getFullName());
            result.put("resolvedBy", r);
        }

        if (alert.getRelatedData() != null && !alert.getRelatedData().isEmpty()) {
            try {
                Map<String, Object> related = objectMapper.readValue(alert.getRelatedData(),
                        new TypeReference<Map<String, Object>>() {});
                result.put("relatedData", related);
                result.put("scoreTrend", extractScoreTrend(related));
            } catch (Exception e) {
                result.put("relatedData", new LinkedHashMap<>());
            }
        } else {
            result.put("relatedData", new LinkedHashMap<>());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Double> extractScoreTrend(Map<String, Object> related) {
        Object trend = related.get("scoreTrend");
        if (trend instanceof List) {
            List<Double> result = new ArrayList<>();
            for (Object item : (List<Object>) trend) {
                if (item instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) item;
                    Object score = m.get("score");
                    Object total = m.get("totalScore");
                    if (score instanceof Number && total instanceof Number && ((Number) total).doubleValue() > 0) {
                        result.add(Math.round(((Number) score).doubleValue() / ((Number) total).doubleValue() * 1000.0) / 10.0);
                    }
                }
            }
            Collections.reverse(result);
            return result;
        }
        return null;
    }

    private String getSeverityText(String s) {
        return switch (s) {
            case "HIGH" -> "严重";
            case "MEDIUM" -> "中等";
            case "LOW" -> "轻微";
            default -> s;
        };
    }

    private String getTypeText(String t) {
        return switch (t) {
            case "CONSECUTIVE_LOW_SCORE" -> "连续低分";
            case "KNOWLEDGE_POINT_LOW" -> "知识点薄弱";
            case "LONG_TIME_NO_EXAM" -> "长期缺考";
            default -> t;
        };
    }
}
