package org.solarframework.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class ExcelConverterTest {

    @TempDir
    Path tempDir;

    private String writeUsersWorkbook() throws Exception {
        Path xlsx = tempDir.resolve("users.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Users");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ID");
            header.createCell(1).setCellValue("Name");
            header.createCell(2).setCellValue("Score");

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(1);
            r1.createCell(1).setCellValue("Alice");
            r1.createCell(2).setCellValue(85.5);

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue(2);
            r2.createCell(1).setCellValue("Bob");
            r2.createCell(2).setCellValue(90);

            try (FileOutputStream fos = new FileOutputStream(xlsx.toFile())) {
                wb.write(fos);
            }
        }
        return xlsx.toString();
    }

    @Test
    void convertCreatesTableAndInsertsRows() throws Exception {
        String xlsxPath = writeUsersWorkbook();
        String jdbcUrl = "jdbc:sqlite:" + tempDir.resolve("out.db");

        // the table does not exist yet, so a bare "DROP TABLE" would fail: must opt out of the drop on first run
        new ExcelConverter(xlsxPath, jdbcUrl).withDrop(false).Convert();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM Users ORDER BY ID")) {

            assertTrue(rs.next());
            assertEquals("1", rs.getString("ID"));
            assertEquals("Alice", rs.getString("Name"));
            assertEquals("85.5", rs.getString("Score"));

            assertTrue(rs.next());
            assertEquals("2", rs.getString("ID"));
            assertEquals("Bob", rs.getString("Name"));
            assertEquals("90", rs.getString("Score"));

            assertFalse(rs.next());
        }
    }

    @Test
    void withRowsFalseSkipsDataInsertion() throws Exception {
        String xlsxPath = writeUsersWorkbook();
        String jdbcUrl = "jdbc:sqlite:" + tempDir.resolve("norows.db");

        new ExcelConverter(xlsxPath, jdbcUrl).withDrop(false).withRows(false).Convert();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS c FROM Users")) {
            rs.next();
            assertEquals(0, rs.getInt("c"));
        }
    }

    @Test
    void withDropFalseSkipsDropWhenTableDoesNotExist() throws Exception {
        String xlsxPath = writeUsersWorkbook();
        String jdbcUrl = "jdbc:sqlite:" + tempDir.resolve("nodrop.db");

        assertDoesNotThrow(() -> new ExcelConverter(xlsxPath, jdbcUrl).withDrop(false).Convert());

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS c FROM Users")) {
            rs.next();
            assertEquals(2, rs.getInt("c"));
        }
    }

    @Test
    void reconvertingWithDropTrueRecreatesAnAlreadyExistingTable() throws Exception {
        String xlsxPath = writeUsersWorkbook();
        String jdbcUrl = "jdbc:sqlite:" + tempDir.resolve("redrop.db");

        new ExcelConverter(xlsxPath, jdbcUrl).withDrop(false).Convert();
        // now that the table exists, the default (withDrop(true)) can drop and recreate it
        new ExcelConverter(xlsxPath, jdbcUrl).Convert();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS c FROM Users")) {
            rs.next();
            assertEquals(2, rs.getInt("c"), "recreated table should hold exactly one fresh copy of the rows, not duplicates");
        }
    }

    @Test
    void sheetNameIsSanitizedIntoTableName() throws Exception {
        Path xlsx = tempDir.resolve("weird.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("My Sheet!");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Col A");
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("value1");
            try (FileOutputStream fos = new FileOutputStream(xlsx.toFile())) {
                wb.write(fos);
            }
        }
        String jdbcUrl = "jdbc:sqlite:" + tempDir.resolve("sanitized.db");

        new ExcelConverter(xlsx.toString(), jdbcUrl).withDrop(false).Convert();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM MySheet")) {
            assertTrue(rs.next());
            assertEquals("value1", rs.getString("ColA"));
        }
    }
}
