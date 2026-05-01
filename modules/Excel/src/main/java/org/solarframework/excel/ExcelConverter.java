package org.solarframework.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("all")
public class ExcelConverter {

    private final String excelInput;
    private final String connectionOutput;
    private boolean withDrop = true;
    private boolean withCreate = true;
    private boolean withRows = true;

    public ExcelConverter(String excelInput, String connectionOutput) {
        this.excelInput = excelInput;
        this.connectionOutput = connectionOutput;
    }

    public ExcelConverter withDrop(boolean drop) {
        withDrop = drop;
        return this;
    }
    public ExcelConverter withCreate(boolean create) {
        withCreate = create;
        return this;
    }
    public ExcelConverter withRows(boolean insert) {
        withRows = insert;
        return this;
    }

    public void Convert() throws Exception {
        try (FileInputStream fis = new FileInputStream(excelInput);
             Workbook workbook = new XSSFWorkbook(fis);
             Connection conn = DriverManager.getConnection(connectionOutput);
             Statement stmt = conn.createStatement()) {
            for (int S = 0; S < workbook.getNumberOfSheets(); S++) {
                Sheet sheet = workbook.getSheetAt(S);
                Row HeaderRow = sheet.getRow(0);
                List<Integer> columns = new ArrayList<>();
                String tableName = removeNonAlphabetic(sheet.getSheetName());
                int ColumnsCount = HeaderRow.getLastCellNum();

                if (withDrop) DropTable(tableName, stmt);

                if (withCreate) CreateTable(sheet, tableName, ColumnsCount, HeaderRow, columns, stmt);

                if (withRows) AddRows(sheet, tableName, ColumnsCount, columns, stmt);
            }
        }
    }

    private void DropTable(String tableName, Statement stmt) throws Exception {
        String DROPSQL = "DROP TABLE " + tableName + ";";
        System.out.println(DROPSQL);
        stmt.execute(DROPSQL);
    }

    private String getDataType(Sheet sheet, int column) {
        List<Row> Rows = new ArrayList<>();
        for (int R = 1; R < sheet.getLastRowNum(); R++) Rows.add(sheet.getRow(R));
        return Rows.stream().allMatch(R -> R.getCell(column).getCellType().equals(CellType.NUMERIC)) ? "NUMERIC" : "TEXT";
    }
    private void CreateTable(Sheet sheet, String tableName, int ColumnsCount, Row HeaderRow, List<Integer> columns, Statement stmt) throws Exception {
        StringBuilder CREATESQL = new StringBuilder("CREATE TABLE " + tableName + " (");
        for (int C = 0; C < ColumnsCount; C++) {
            if (!removeNonAlphabetic(getStringValue(HeaderRow.getCell(C))).isEmpty()) {
                columns.add(C);
                CREATESQL.append(", ").append(removeNonAlphabetic(getStringValue(HeaderRow.getCell(C)))).append(" ").append(getDataType(sheet, C));
            }
        }
        CREATESQL = new StringBuilder(CREATESQL.toString().replaceFirst(", ", "") + ");");
        System.out.println(CREATESQL);
        stmt.execute(CREATESQL.toString());
    }
    private void AddRows(Sheet sheet, String tableName, int ColumnsCount, List<Integer> columns, Statement stmt) throws Exception {
        for (int R = 1; R <= sheet.getLastRowNum(); R++) {
            Row row = sheet.getRow(R);
            AddRow(tableName, ColumnsCount, columns, stmt, row);
        }
    }

    private void AddRow(String tableName, int ColumnsCount, List<Integer> columns, Statement stmt, Row row) throws SQLException {
        if (!getStringValue(row.getCell(0)).isEmpty()) {
            StringBuilder INSERTSQL = new StringBuilder("INSERT INTO " + tableName + " VALUES (");
            for (int C = 0; C < ColumnsCount; C++) {
                if (columns.contains(C)) INSERTSQL.append(", '").append(getStringValue(row.getCell(C)).replaceAll("'", "''")).append("'");
            }
            INSERTSQL = new StringBuilder(INSERTSQL.toString().replaceFirst(", ", "") + ");");
            System.out.println(INSERTSQL);
            stmt.execute(INSERTSQL.toString());
        }
    }

    private String removeNonAlphabetic(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9]", "");
    }
    private String getStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> removeTrailingZero(cell.getNumericCellValue() + "");
            case BOOLEAN -> cell.getBooleanCellValue() + "";
            default -> "";
        };
    }
    private String removeTrailingZero(String input) {
        return input.endsWith(".0") ? input.substring(0, input.length() - 2) : input;
    }

}