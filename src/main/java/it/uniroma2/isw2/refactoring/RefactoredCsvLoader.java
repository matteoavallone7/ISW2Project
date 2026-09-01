package it.uniroma2.isw2.refactoring;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RefactoredCsvLoader {


    public List<Map<String, String>> load(String csvPath)
            throws IOException {

        List<Map<String, String>> rows = new ArrayList<>();

        Path path = Path.of(csvPath);

        try (BufferedReader reader = Files.newBufferedReader(path)) {

            String headerLine = reader.readLine();

            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException(
                        "CSV file is empty: " + csvPath
                );
            }

            List<String> headers = parseLine(headerLine);
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                List<String> values = parseLine(line);
                Map<String, String> row = new LinkedHashMap<>();

                for (int i = 0; i < headers.size(); i++) {

                    String value = i < values.size() ? values.get(i) : "";
                    row.put(headers.get(i), value);
                }

                rows.add(row);
            }
        }

        return rows;
    }


    public void write(
            List<Map<String, String>> rows,
            String csvPath) throws IOException {

        if (rows == null || rows.isEmpty()) {
            return;
        }

        Path path = Path.of(csvPath);

        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        Map<String, String> firstRow = rows.get(0);

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {

            // Header
            writer.write(String.join(",", firstRow.keySet()));
            writer.newLine();

            // Data
            for (Map<String, String> row : rows) {

                List<String> values = new ArrayList<>();

                for (String header : firstRow.keySet()) {

                    String value = row.getOrDefault(header, "");
                    values.add(csvEscape(value));
                }

                writer.write(String.join(",", values));
                writer.newLine();
            }
        }
    }


    private List<String> parseLine(String line) {

        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {

            char c = line.charAt(i);

            if (c == '"') {
                if (insideQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;

                } else {
                    insideQuotes = !insideQuotes;
                }

            } else if (c == ',' && !insideQuotes) {

                values.add(current.toString());
                current.setLength(0);

            } else {
                current.append(c);
            }
        }

        values.add(current.toString());
        return values;
    }


    private String csvEscape(String value) {

        if (value == null) {
            return "";
        }

        if (value.contains(",")
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r")) {

            return "\"" +
                    value.replace("\"", "\"\"") +
                    "\"";
        }

        return value;
    }

}