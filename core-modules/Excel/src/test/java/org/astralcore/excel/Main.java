package org.solarframework.excel;

public class Main {

    static void main(String[] args) throws Exception {
        ExcelConverter EC = new ExcelConverter("input.xlsx", "jdbc:mysql://00.00.00.00:3306/myschema");
        EC.withDrop(true); // Enable drop of tables based on sheets
        EC.withCreate(true); // Enable creation of tables based on sheets
        EC.withRows(true); // Enable insert of rows based on sheets
        EC.Convert();
    }
}
