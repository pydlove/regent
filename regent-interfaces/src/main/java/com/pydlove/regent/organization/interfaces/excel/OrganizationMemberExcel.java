package com.pydlove.regent.organization.interfaces.excel;

import com.pydlove.regent.organization.application.query.OrganizationMemberView;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.shared.domain.DomainException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 组织成员 Excel 模板、导出和导入结果文件。
 *
 * <p>这是传输层格式，不进入领域层。导入只读取第一个工作表，未知列会忽略；
 * 每行只能填写一个组织角色，多角色账号回导时按角色拆成多行。
 */
public final class OrganizationMemberExcel {
    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final int MAX_IMPORT_ROWS = 50;
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    private static final List<String> ROLE_LABELS = List.of("组织管理员", "项目经理", "普通成员");
    private static final Map<OrganizationRole, String> ROLE_TEXT = Map.of(
            OrganizationRole.ORG_ADMIN, "组织管理员",
            OrganizationRole.PROJECT_MANAGER, "项目经理",
            OrganizationRole.ORG_MEMBER, "普通成员",
            OrganizationRole.ORG_OWNER, "组织所有者");
    private static final Map<ProjectMemberRole, String> PROJECT_ROLE_TEXT = Map.ofEntries(
            Map.entry(ProjectMemberRole.OWNER, "项目负责人"),
            Map.entry(ProjectMemberRole.BUSINESS_OWNER, "业务负责人"),
            Map.entry(ProjectMemberRole.PRODUCT, "产品负责人"),
            Map.entry(ProjectMemberRole.TECH_LEAD, "技术负责人"),
            Map.entry(ProjectMemberRole.ARCHITECT, "架构师"),
            Map.entry(ProjectMemberRole.DEVELOPER, "开发"),
            Map.entry(ProjectMemberRole.QA, "测试负责人"),
            Map.entry(ProjectMemberRole.TESTER, "测试人员"),
            Map.entry(ProjectMemberRole.OPS, "运维/SRE"),
            Map.entry(ProjectMemberRole.SECURITY, "安全/合规"),
            Map.entry(ProjectMemberRole.RELEASE_MANAGER, "发布负责人"),
            Map.entry(ProjectMemberRole.VIEWER, "观察者"),
            Map.entry(ProjectMemberRole.AUDITOR, "审计员"));

    private OrganizationMemberExcel() {
    }

    public static byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet importSheet = workbook.createSheet("成员导入");
            createHeader(importSheet, List.of("邮箱", "姓名", "组织角色"));
            setWidths(importSheet, 34, 20, 20);
            addRoleValidation(importSheet);
            addTemplateHint(importSheet);

            Sheet guide = workbook.createSheet("填写说明");
            setWidths(guide, 22, 88);
            Row title = guide.createRow(0);
            title.createCell(0).setCellValue("组织成员导入说明");
            title.getCell(0).setCellStyle(titleStyle(workbook));
            guide.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 1));
            guide.createRow(1).createCell(0)
                    .setCellValue("单次最多导入 " + MAX_IMPORT_ROWS + " 行，文件不超过 2MB。");
            guide.getRow(1).createCell(1).setCellValue(
                    "只填写“成员导入”工作表；请勿修改表头。邮箱重复、角色不合法或缺少必填项的行会记录在结果文件中。");
            guide.createRow(2).createCell(0).setCellValue("已有账号");
            guide.getRow(2).createCell(1)
                    .setCellValue("按邮箱匹配已有账号，只补组织角色，不覆盖账号的登录名和显示名称。");
            guide.createRow(3).createCell(0).setCellValue("新账号");
            guide.getRow(3).createCell(1)
                    .setCellValue("邮箱不存在时自动创建账号，姓名必填；初始密码统一为 12345678，首次登录必须修改。");
            guide.createRow(4).createCell(0).setCellValue("可填组织角色");
            guide.getRow(4).createCell(1).setCellValue(String.join("、", ROLE_LABELS));
            guide.createRow(5).createCell(0).setCellValue("组织所有者");
            guide.getRow(5).createCell(1)
                    .setCellValue("不能通过 Excel 授予组织所有者，请在成员页单独确认后转移所有权。");
            return write(workbook);
        } catch (IOException exception) {
            throw new IllegalStateException("生成组织成员导入模板失败", exception);
        }
    }

    public static byte[] export(List<OrganizationMemberView> members) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("组织成员");
            createHeader(sheet, List.of(
                    "邮箱", "显示名称", "登录名", "组织角色", "项目角色", "账号状态"));
            setWidths(sheet, 34, 18, 20, 24, 48, 16);
            int rowIndex = 1;
            for (OrganizationMemberView member : members) {
                Row row = sheet.createRow(rowIndex++);
                setText(row, 0, member.user().getEmail() == null
                        ? "" : member.user().getEmail().value());
                setText(row, 1, member.user().getDisplayName().value());
                setText(row, 2, member.user().getUsername().value());
                setText(row, 3, member.roles().stream()
                        .map(OrganizationMemberExcel::roleText)
                        .distinct()
                        .reduce((left, right) -> left + "、" + right)
                        .orElse(""));
                setText(row, 4, member.projects().stream()
                        .map(project -> project.projectName() + "·" + project.roles().stream()
                                .map(role -> PROJECT_ROLE_TEXT.getOrDefault(role, role.name()))
                                .distinct()
                                .reduce((left, right) -> left + "、" + right)
                                .orElse("未分配项目角色"))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("尚未加入项目"));
                setText(row, 5,
                        member.user().mustChangePassword() ? "待首次登录" : "已启用");
            }
            finishTable(sheet, 1, rowIndex - 1, 6);
            return write(workbook);
        } catch (IOException exception) {
            throw new IllegalStateException("导出组织成员失败", exception);
        }
    }

    public static List<ParsedRow> parse(InputStream input) {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new DomainException("Excel 中没有工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Header header = findHeader(sheet);
            List<ParsedRow> rows = new ArrayList<>();
            Set<String> seenEmails = new HashSet<>();
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            for (int rowIndex = header.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                String email = cell(formatter, row, header.emailColumn());
                String displayName = cell(formatter, row, header.nameColumn());
                String roleText = cell(formatter, row, header.roleColumn());
                if (email.isEmpty() && displayName.isEmpty() && roleText.isEmpty()) continue;
                if (rows.size() >= MAX_IMPORT_ROWS) {
                    throw new DomainException("单次最多导入 " + MAX_IMPORT_ROWS + " 行");
                }

                String error = null;
                OrganizationRole role = null;
                if (email.isEmpty()) {
                    error = "邮箱不能为空";
                } else {
                    String normalizedEmail = email.toLowerCase(Locale.ROOT);
                    if (!seenEmails.add(normalizedEmail)) error = "文件内邮箱重复";
                }
                if (roleText.isEmpty()) {
                    error = appendError(error, "组织角色不能为空");
                } else {
                    try {
                        role = parseRole(roleText);
                    } catch (DomainException exception) {
                        error = appendError(error, exception.getMessage());
                    }
                }
                rows.add(new ParsedRow(
                        rowIndex + 1, email, displayName, roleText, role, error));
            }
            if (rows.isEmpty()) {
                throw new DomainException("Excel 中没有可导入的成员数据");
            }
            return List.copyOf(rows);
        } catch (DomainException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof DomainException domainException) throw domainException;
            throw new DomainException("Excel 文件无法解析，请使用下载的模板重新填写");
        }
    }

    public static byte[] importResult(List<ResultRow> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("导入结果");
            createHeader(sheet, List.of(
                    "行号", "邮箱", "姓名", "组织角色", "结果", "登录名", "初始密码", "说明"));
            setWidths(sheet, 9, 34, 18, 18, 16, 22, 24, 58);
            CellStyle failureStyle = failureStyle(workbook);
            for (int index = 0; index < rows.size(); index++) {
                ResultRow result = rows.get(index);
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(result.rowNumber());
                setText(row, 1, result.email());
                setText(row, 2, result.displayName());
                setText(row, 3, result.roleText());
                setText(row, 4, result.outcome());
                setText(row, 5, result.username());
                setText(row, 6, result.initialPassword());
                setText(row, 7, result.message());
                if ("失败".equals(result.outcome())) {
                    row.getCell(4).setCellStyle(failureStyle);
                    row.getCell(7).setCellStyle(failureStyle);
                }
            }
            finishTable(sheet, 1, rows.size(), 8);

            Sheet guide = workbook.createSheet("安全提示");
            setWidths(guide, 24, 88);
            guide.createRow(0).createCell(0).setCellValue("请妥善处理本文件");
            guide.getRow(0).getCell(0).setCellStyle(titleStyle(workbook));
            guide.createRow(1).createCell(0).setCellValue("初始密码");
            guide.getRow(1).createCell(1)
                    .setCellValue("新建账号的初始密码统一为 12345678；首次登录后必须修改。");
            guide.createRow(2).createCell(0).setCellValue("首次登录");
            guide.getRow(2).createCell(1)
                    .setCellValue("请通过安全渠道把账号和初始密码交给本人，并提醒尽快修改。");
            guide.createRow(3).createCell(0).setCellValue("已有账号");
            guide.getRow(3).createCell(1)
                    .setCellValue("已有账号仅新增组织角色，不会重置密码或覆盖显示名称。");
            return write(workbook);
        } catch (IOException exception) {
            throw new IllegalStateException("生成组织成员导入结果失败", exception);
        }
    }

    static OrganizationRole parseRole(String value) {
        String normalized = value == null ? "" : value.trim();
        return switch (normalized) {
            case "ORG_ADMIN", "组织管理员", "管理员" -> OrganizationRole.ORG_ADMIN;
            case "PROJECT_MANAGER", "项目经理" -> OrganizationRole.PROJECT_MANAGER;
            case "ORG_MEMBER", "普通成员", "成员" -> OrganizationRole.ORG_MEMBER;
            case "ORG_OWNER", "组织所有者" ->
                    throw new DomainException("不能通过 Excel 导入组织所有者");
            default -> throw new DomainException("不支持的组织角色：" + normalized);
        };
    }

    public static String roleLabel(OrganizationRole role) {
        return roleText(role);
    }

    private static Header findHeader(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), 8); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            Map<String, Integer> columns = new HashMap<>();
            for (Cell cell : row) {
                columns.put(normalizeHeader(formatter.formatCellValue(cell)),
                        cell.getColumnIndex());
            }
            Integer email = headerColumn(columns, "邮箱", "email");
            Integer name = headerColumn(columns, "姓名", "显示名称", "name", "displayname");
            Integer role = headerColumn(columns, "组织角色", "role", "organizationrole");
            if (email != null && name != null && role != null) {
                return new Header(rowIndex, email, name, role);
            }
        }
        throw new DomainException("Excel 表头缺少“邮箱、姓名、组织角色”，请使用下载的模板");
    }

    private static Integer headerColumn(Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(normalizeHeader(name));
            if (index != null) return index;
        }
        return null;
    }

    private static String normalizeHeader(String value) {
        return value == null ? "" : value
                .replace("*", "")
                .replace(" ", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String cell(DataFormatter formatter, Row row, int column) {
        Cell cell = row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static String appendError(String existing, String next) {
        return existing == null || existing.isBlank() ? next : existing + "；" + next;
    }

    private static String roleText(OrganizationRole role) {
        return ROLE_TEXT.getOrDefault(role, role.name());
    }

    private static void createHeader(Sheet sheet, List<String> headers) {
        Workbook workbook = sheet.getWorkbook();
        CellStyle style = headerStyle(workbook);
        Row header = sheet.createRow(0);
        header.setHeightInPoints(24);
        for (int index = 0; index < headers.size(); index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(headers.get(index));
            cell.setCellStyle(style);
        }
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        Font font = workbook.createFont();
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 15);
        style.setFont(font);
        return style;
    }

    private static CellStyle failureStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setColor(IndexedColors.DARK_RED.getIndex());
        style.setFont(font);
        return style;
    }

    private static void addRoleValidation(Sheet sheet) {
        var helper = sheet.getDataValidationHelper();
        var constraint = helper.createExplicitListConstraint(ROLE_LABELS.toArray(String[]::new));
        var validation = helper.createValidation(
                constraint, new CellRangeAddressList(1, MAX_IMPORT_ROWS, 2, 2));
        validation.setShowErrorBox(true);
        validation.createErrorBox("组织角色不合法", "请从下拉列表中选择组织角色");
        sheet.addValidationData(validation);
    }

    private static void addTemplateHint(Sheet sheet) {
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, MAX_IMPORT_ROWS, 0, 2));
    }

    private static void finishTable(Sheet sheet, int firstDataRow, int lastDataRow, int columns) {
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, Math.max(firstDataRow, lastDataRow), 0, columns - 1));
    }

    private static void setWidths(Sheet sheet, int... widths) {
        for (int index = 0; index < widths.length; index++) {
            sheet.setColumnWidth(index, widths[index] * 256);
        }
    }

    private static void setText(Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private static byte[] write(Workbook workbook) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }

    public record ParsedRow(
            int rowNumber,
            String email,
            String displayName,
            String roleText,
            OrganizationRole role,
            String error) {
    }

    public record ResultRow(
            int rowNumber,
            String email,
            String displayName,
            String roleText,
            String outcome,
            String username,
            String initialPassword,
            String message) {
    }

    private record Header(
            int rowIndex,
            int emailColumn,
            int nameColumn,
            int roleColumn) {
    }
}
