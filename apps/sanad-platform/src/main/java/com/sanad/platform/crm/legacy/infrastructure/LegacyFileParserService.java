package com.sanad.platform.crm.legacy.infrastructure;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.sanad.platform.crm.legacy.infrastructure.LegacySupport.*;

@Service
public class LegacyFileParserService {

    private final LegacySupport support;

    public LegacyFileParserService(LegacySupport support) {
        this.support = support;
    }

    public ParsedTable parseImportFile(String filename, String contentType, byte[] bytes) {
        String lowerFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (lowerFilename.endsWith(".csv") || lowerContentType.contains("csv")) {
            return parseCsv(bytes);
        }
        if (lowerFilename.endsWith(".xlsx")
                || lowerContentType.contains("spreadsheetml")) {
            return parseXlsx(bytes);
        }
        throw bad("CRM import supports CSV and XLSX files only");
    }

    public ParsedTable parseCsv(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        return tableFromMatrix(csvRecords(text));
    }

    public ParsedTable parseXlsx(byte[] bytes) {
        try {
            Map<String, byte[]> entries = readXlsxEntries(bytes);
            List<String> sharedStrings = xlsxSharedStrings(entries.get("xl/sharedStrings.xml"));
            String sheetName = entries.keySet().stream()
                    .filter(name -> name.startsWith("xl/worksheets/") && name.endsWith(".xml"))
                    .sorted()
                    .findFirst()
                    .orElseThrow(() -> bad("XLSX does not contain a worksheet"));
            org.w3c.dom.Document sheet = safeXml(entries.get(sheetName));
            org.w3c.dom.NodeList rowNodes = sheet.getElementsByTagNameNS("*", "row");
            List<List<String>> matrix = new ArrayList<>();
            for (int rowIndex = 0; rowIndex < rowNodes.getLength(); rowIndex++) {
                org.w3c.dom.Element rowElement = (org.w3c.dom.Element) rowNodes.item(rowIndex);
                org.w3c.dom.NodeList cells = rowElement.getElementsByTagNameNS("*", "c");
                List<String> row = new ArrayList<>();
                for (int cellIndex = 0; cellIndex < cells.getLength(); cellIndex++) {
                    org.w3c.dom.Element cell = (org.w3c.dom.Element) cells.item(cellIndex);
                    int column = xlsxColumn(cell.getAttribute("r"));
                    if (column >= MAX_IMPORT_COLUMNS) {
                        throw bad("XLSX exceeds the 100-column limit");
                    }
                    while (row.size() <= column) row.add("");
                    row.set(column, xlsxCellValue(cell, sharedStrings));
                }
                matrix.add(row);
                if (matrix.size() > MAX_IMPORT_ROWS + 1) {
                    throw bad("CRM import exceeds the 10000-row limit");
                }
            }
            return tableFromMatrix(matrix);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw bad("Invalid or unsupported XLSX file");
        }
    }

    public Map<String, String> resolveMapping(
            UUID tenantId, String entityType, List<String> headers, String mappingJson) {
        Map<String, String> requested = new LinkedHashMap<>();
        if (mappingJson != null && !mappingJson.isBlank()) {
            if (mappingJson.length() > 50_000) throw bad("CRM import mapping is too large");
            try {
                requested.putAll(support.objectMapper.readValue(
                        mappingJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() { }));
            } catch (IOException exception) {
                throw bad("CRM import mapping must be a JSON object of source header to target field");
            }
        } else {
            for (String header : headers) {
                requested.put(header, resolveImportTarget(entityType, header));
            }
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        Set<String> targets = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : requested.entrySet()) {
            String source = entry.getKey().trim();
            if (!headers.contains(source)) throw bad("CRM import mapping references an unknown header: " + source);
            String target = resolveImportTarget(entityType, entry.getValue());
            if (!targets.add(target)) throw bad("CRM import mapping contains a duplicate target: " + target);
            if (target.startsWith("custom.")) {
                String key = target.substring("custom.".length());
                if (support.scalarLong(
                        "SELECT COUNT(*) FROM crm_custom_field_definitions " +
                                "WHERE tenant_id=:tenantId AND entity_type=:entityType AND field_key=:fieldKey AND active=TRUE",
                        p().addValue("tenantId", tenantId).addValue("entityType", entityType)
                                .addValue("fieldKey", key)) != 1) {
                    throw bad("CRM import mapping references an unknown custom field: " + key);
                }
            }
            result.put(source, target);
        }
        for (String required : REQUIRED_IMPORT_FIELDS.get(entityType)) {
            if (!targets.contains(required)) {
                throw bad("CRM import mapping is missing required target: " + required);
            }
        }
        return result;
    }

    public Map<String, String> mappedValues(
            Map<String, String> sourceRow, Map<String, String> mapping) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            result.put(entry.getValue(), sourceRow.getOrDefault(entry.getKey(), ""));
        }
        return result;
    }

    public Map<String, Object> customImportValues(Map<String, String> values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().startsWith("custom.")) {
                result.put(entry.getKey().substring("custom.".length()), entry.getValue());
            }
        }
        return result;
    }

    // ── Private CSV/XLSX helpers ───────────────────────────────────────────

    private List<List<String>> csvRecords(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (current == '"' && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else if (current == '"') {
                    quoted = false;
                } else {
                    value.append(current);
                }
            } else if (current == '"' && value.length() == 0) {
                quoted = true;
            } else if (current == ',') {
                row.add(value.toString());
                value.setLength(0);
            } else if (current == '\n' || current == '\r') {
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                row.add(value.toString());
                value.setLength(0);
                records.add(row);
                row = new ArrayList<>();
            } else {
                value.append(current);
            }
        }
        if (quoted) throw bad("CRM CSV contains an unterminated quoted field");
        if (value.length() > 0 || !row.isEmpty()) {
            row.add(value.toString());
            records.add(row);
        }
        return records;
    }

    private Map<String, byte[]> readXlsxEntries(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        int expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.contains("..") || name.startsWith("/") || entries.size() >= 500) {
                    throw new IOException("Unsafe XLSX archive");
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    expanded += read;
                    if (expanded > MAX_EXPANDED_XLSX_BYTES) {
                        throw new IOException("XLSX expanded size limit exceeded");
                    }
                    output.write(buffer, 0, read);
                }
                entries.put(name, output.toByteArray());
            }
        }
        return entries;
    }

    private org.w3c.dom.Document safeXml(byte[] bytes) throws Exception {
        if (bytes == null) throw new IOException("Required XLSX XML is missing");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private List<String> xlsxSharedStrings(byte[] bytes) throws Exception {
        if (bytes == null) return List.of();
        org.w3c.dom.NodeList items = safeXml(bytes).getElementsByTagNameNS("*", "si");
        List<String> values = new ArrayList<>();
        for (int index = 0; index < items.getLength(); index++) {
            org.w3c.dom.Element item = (org.w3c.dom.Element) items.item(index);
            org.w3c.dom.NodeList texts = item.getElementsByTagNameNS("*", "t");
            StringBuilder value = new StringBuilder();
            for (int textIndex = 0; textIndex < texts.getLength(); textIndex++) {
                value.append(texts.item(textIndex).getTextContent());
            }
            values.add(value.toString());
        }
        return values;
    }

    private int xlsxColumn(String reference) {
        int result = 0;
        int letters = 0;
        while (letters < reference.length() && Character.isLetter(reference.charAt(letters))) {
            result = result * 26 + Character.toUpperCase(reference.charAt(letters)) - 'A' + 1;
            letters++;
        }
        if (letters == 0) throw bad("Invalid XLSX cell reference");
        return result - 1;
    }

    private String xlsxCellValue(org.w3c.dom.Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        org.w3c.dom.NodeList inlineText = cell.getElementsByTagNameNS("*", "t");
        if ("inlineStr".equals(type) && inlineText.getLength() > 0) {
            return inlineText.item(0).getTextContent();
        }
        org.w3c.dom.NodeList values = cell.getElementsByTagNameNS("*", "v");
        if (values.getLength() == 0) return "";
        String value = values.item(0).getTextContent();
        if ("s".equals(type)) {
            int index;
            try {
                index = Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw bad("Invalid XLSX shared string index");
            }
            if (index < 0 || index >= sharedStrings.size()) {
                throw bad("Invalid XLSX shared string index");
            }
            return sharedStrings.get(index);
        }
        if ("b".equals(type)) return "1".equals(value) ? "true" : "false";
        return value;
    }

    private ParsedTable tableFromMatrix(List<List<String>> matrix) {
        if (matrix.isEmpty()) throw bad("CRM import file is empty");
        List<String> headers = matrix.get(0).stream().map(String::trim).toList();
        if (headers.isEmpty() || headers.size() > MAX_IMPORT_COLUMNS
                || headers.stream().anyMatch(String::isBlank)
                || new LinkedHashSet<>(headers).size() != headers.size()) {
            throw bad("CRM import headers must be unique, non-empty, and limited to 100");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = 1; index < matrix.size(); index++) {
            List<String> source = matrix.get(index);
            if (source.stream().allMatch(String::isBlank)) continue;
            if (source.size() > headers.size()) {
                throw bad("CRM import row has more columns than the header");
            }
            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                row.put(headers.get(column),
                        column < source.size() ? source.get(column).trim() : "");
            }
            rows.add(row);
            if (rows.size() > MAX_IMPORT_ROWS) {
                throw bad("CRM import exceeds the 10000-row limit");
            }
        }
        return new ParsedTable(headers, rows);
    }

    // ── Import target resolution ───────────────────────────────────────────

    private String resolveImportTarget(String entityType, String requested) {
        if (requested == null || requested.isBlank()) throw bad("CRM import target cannot be blank");
        String trimmed = requested.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("custom.")) {
            String key = trimmed.substring(trimmed.indexOf('.') + 1).trim();
            if (!key.matches("[A-Za-z][A-Za-z0-9_]{1,119}")) {
                throw bad("Invalid CRM custom-field import target");
            }
            return "custom." + key;
        }
        String canonical = canonical(trimmed);
        for (String allowed : IMPORT_FIELDS.get(entityType)) {
            if (canonical(allowed).equals(canonical)) return allowed;
        }
        String alias = switch (entityType + ":" + canonical) {
            case "ACCOUNT:name", "LEAD:name" -> "displayName";
            case "CONTACT:name", "CONTACT:firstname" -> "givenName";
            case "CONTACT:lastname", "CONTACT:surname" -> "familyName";
            case "CONTACT:email" -> "primaryEmail";
            case "CONTACT:phone" -> "primaryPhone";
            case "OPPORTUNITY:opportunityname" -> "name";
            case "ACTIVITY:type" -> "activityType";
            default -> null;
        };
        if (alias == null) throw bad("Unsupported CRM import target: " + requested);
        return alias;
    }
}
