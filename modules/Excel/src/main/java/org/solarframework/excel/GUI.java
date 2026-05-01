package org.solarframework.excel;

import javax.swing.*;
import java.awt.*;

public class GUI {

    public static void main(String[] args) {
        JFrame frame = new JFrame("Excel Converter");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridLayout(0, 1, 10, 10));

        JTextField excelPath = new JTextField(50);
        JTextField connectionString = new JTextField(50);
        JCheckBox dropTable = new JCheckBox("Drop Tables (before create)", true);
        JCheckBox createTable = new JCheckBox("Create Tables", true);
        JCheckBox insertRows = new JCheckBox("Insert Rows", true);
        JButton convertButton = new JButton("Convert");

        frame.add(new JLabel("Excel File Path:"));
        frame.add(excelPath);
        frame.add(new JLabel("Connection String:"));
        frame.add(connectionString);
        frame.add(dropTable);
        frame.add(createTable);
        frame.add(insertRows);
        frame.add(convertButton);

        convertButton.addActionListener(e -> {
            try {
                ExcelConverter converter = new ExcelConverter(excelPath.getText(), connectionString.getText());
                if (!dropTable.isSelected()) converter.withDrop(true);
                if (!createTable.isSelected()) converter.withCreate(true);
                if (!insertRows.isSelected()) converter.withRows(true);
                converter.Convert();
                JOptionPane.showMessageDialog(frame, "Conversion completed successfully!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
