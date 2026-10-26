package com.pydlove.regent.organization.interfaces.excel;

import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.shared.domain.DomainException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganizationMemberExcelTest {

    @Test
    @DisplayName("模板包含导入页、填写说明和角色下拉")
    void templateContainsImportSheetAndGuide() throws IOException {
        byte[] content = OrganizationMemberExcel.template();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertEquals("成员导入", workbook.getSheetAt(0).getSheetName());
            assertEquals("邮箱", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("组织角色", workbook.getSheetAt(0).getRow(0).getCell(2).getStringCellValue());
            assertEquals("填写说明", workbook.getSheetAt(1).getSheetName());
        }
    }

    @Test
    @DisplayName("导入解析保留错误行，重复邮箱和非法角色不会拖垮整批")
    void parseKeepsInvalidRows() throws IOException {
        byte[] content;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("成员导入");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("邮箱");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("组织角色");
            addRow(sheet, 1, "member@example.com", "成员甲", "项目经理");
            addRow(sheet, 2, "member@example.com", "成员乙", "普通成员");
            addRow(sheet, 3, "owner@example.com", "成员丙", "组织所有者");
            content = write(workbook);
        }

        var rows = OrganizationMemberExcel.parse(new ByteArrayInputStream(content));

        assertEquals(3, rows.size());
        assertNull(rows.get(0).error());
        assertEquals(OrganizationRole.PROJECT_MANAGER, rows.get(0).role());
        assertEquals("文件内邮箱重复", rows.get(1).error());
        assertTrue(rows.get(2).error().contains("不能通过 Excel 导入组织所有者"));
    }

    @Test
    @DisplayName("角色支持中文和枚举编码，拒绝未知角色")
    void parseRoles() {
        assertEquals(OrganizationRole.ORG_ADMIN,
                OrganizationMemberExcel.parseRole("组织管理员"));
        assertEquals(OrganizationRole.PROJECT_MANAGER,
                OrganizationMemberExcel.parseRole("PROJECT_MANAGER"));
        assertEquals(OrganizationRole.ORG_MEMBER,
                OrganizationMemberExcel.parseRole("普通成员"));
        assertThrows(DomainException.class,
                () -> OrganizationMemberExcel.parseRole("超级管理员"));
    }

    @Test
    @DisplayName("缺少必填表头时给出可执行的错误")
    void rejectsMissingHeaders() throws IOException {
        byte[] content;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("成员导入");
            sheet.createRow(0).createCell(0).setCellValue("邮箱");
            content = write(workbook);
        }

        DomainException error = assertThrows(DomainException.class,
                () -> OrganizationMemberExcel.parse(new ByteArrayInputStream(content)));

        assertTrue(error.getMessage().contains("邮箱、姓名、组织角色"));
    }

    private static void addRow(Sheet sheet, int index, String email, String name, String role) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(email);
        row.createCell(1).setCellValue(name);
        row.createCell(2).setCellValue(role);
    }

    private static byte[] write(XSSFWorkbook workbook) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }
}
