package com.forecast.services.ingestion.connector;

import com.forecast.models.RawSalesData;
import com.forecast.models.exceptions.ErrorCode;
import com.forecast.models.exceptions.ForecastingException;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Logger;

/**
 * CSV-backed DataSourceConnector.
 *
 * Reads a RFC-4180 CSV file with a required header row.
 * The expected column order (configurable via Builder) defaults to:
 *
 *   sale_id, product_id, store_id, sale_date, quantity_sold,
 *   unit_price, revenue, region
 *
 * Construction (Builder pattern):
 *
 *   IDataConnector conn = new CsvDataSourceConnector.Builder()
 *       .filePath(Paths.get("/data/sales/2024_sales.csv"))
 *       .delimiter(',')
 *       .hasHeaderRow(true)
 *       .dateFormat("yyyy-MM-dd")
 *       .retryPolicy(RetryPolicy.DEFAULT)
 *       .sourceName("csv-sales-2024")
 *       .build();
 *
 *   conn.connect();
 *   List<RawSalesData> rows = conn.fetchSalesData();
 *   conn.disconnect();
 *
 * Note: connection pool size and socket timeout are inherited from the
 * parent builder but have no effect for CSV — they are stored for
 * interface consistency and future extensibility.
 */
public final class CsvDataSourceConnector extends DataSourceConnector {

    private static final Logger LOG = Logger.getLogger(CsvDataSourceConnector.class.getName());

    // ── Column index constants (default header order) ───────────────
    private static final int COL_SALE_ID      = 0;
    private static final int COL_PRODUCT_ID   = 1;
    private static final int COL_STORE_ID     = 2;
    private static final int COL_SALE_DATE    = 3;
    private static final int COL_QTY_SOLD     = 4;
    private static final int COL_UNIT_PRICE   = 5;
    private static final int COL_REVENUE      = 6;
    private static final int COL_REGION       = 7;

    // ── CSV-specific config ─────────────────────────────────────────
    private final Path    filePath;
    private final char    delimiter;
    private final boolean hasHeaderRow;

    // ── Runtime state ───────────────────────────────────────────────
    private BufferedReader reader;

    private CsvDataSourceConnector(Builder b) {
        super(b);
        this.filePath    = b.filePath;
        this.delimiter   = b.delimiter;
        this.hasHeaderRow = b.hasHeaderRow;
    }

    // ── Hook implementations ────────────────────────────────────────

    @Override
    protected void openConnection() throws Exception {
        if (!Files.exists(filePath)) {
            throw new IOException("CSV file not found: " + filePath.toAbsolutePath());
        }
        if (!Files.isReadable(filePath)) {
            throw new IOException("CSV file is not readable: " + filePath.toAbsolutePath());
        }
        reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8);
        LOG.fine("Opened CSV file: " + filePath.toAbsolutePath());
    }

    @Override
    protected List<RawSalesData> doFetchSalesData() {
        List<RawSalesData> results = new ArrayList<>();
        int lineNumber = 0;

        try {
            String line;
            // Skip header row if present
            if (hasHeaderRow) {
                reader.readLine();
                lineNumber++;
            }

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    results.add(parseLine(line, lineNumber));
                } catch (IllegalArgumentException | DateTimeParseException ex) {
                    // Log and skip malformed rows — do not halt the whole file
                    LOG.warning("Skipping malformed CSV row " + lineNumber
                                + " in [" + sourceName + "]: " + ex.getMessage());
                }
            }

        } catch (IOException ex) {
            throw new ForecastingException(
                    ErrorCode.DATA_SOURCE_UNAVAILABLE,
                    "Failed reading CSV at line " + lineNumber + " in [" + sourceName + "]", ex);
        }

        LOG.info("Parsed " + results.size() + " records from CSV [" + sourceName + "]");
        return Collections.unmodifiableList(results);
    }

    @Override
    protected void closeConnection() throws Exception {
        if (reader != null) {
            reader.close();
            reader = null;
        }
    }

    // ── CSV parsing ─────────────────────────────────────────────────

    private RawSalesData parseLine(String line, int lineNumber) {
        String[] fields = line.split(String.valueOf(delimiter), -1);

        if (fields.length < 8) {
            throw new IllegalArgumentException(
                    "Expected 8 columns, found " + fields.length + " at line " + lineNumber);
        }

        return new RawSalesData.Builder()
                .saleId(parseLong(fields[COL_SALE_ID].trim(), "sale_id", lineNumber))
                .productId(requireNonBlank(fields[COL_PRODUCT_ID].trim(), "product_id", lineNumber))
                .storeId(requireNonBlank(fields[COL_STORE_ID].trim(), "store_id", lineNumber))
                .saleDate(LocalDate.parse(fields[COL_SALE_DATE].trim()))
                .quantitySold(parseInt(fields[COL_QTY_SOLD].trim(), "quantity_sold", lineNumber))
                .unitPrice(new BigDecimal(fields[COL_UNIT_PRICE].trim()))
                .revenue(new BigDecimal(fields[COL_REVENUE].trim()))
                .region(fields[COL_REGION].trim())
                .build();
    }

    private long parseLong(String val, String field, int line) {
        try { return Long.parseLong(val); }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid long for " + field + " at line " + line + ": " + val);
        }
    }

    private int parseInt(String val, String field, int line) {
        try { return Integer.parseInt(val); }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid int for " + field + " at line " + line + ": " + val);
        }
    }

    private String requireNonBlank(String val, String field, int line) {
        if (val == null || val.isEmpty())
            throw new IllegalArgumentException("Blank value for required field " + field + " at line " + line);
        return val;
    }

    // ── Builder ─────────────────────────────────────────────────────

    public static final class Builder extends AbstractBuilder<Builder> {

        private Path    filePath;
        private char    delimiter    = ',';
        private boolean hasHeaderRow = true;

        public Builder filePath(Path val) {
            this.filePath = Objects.requireNonNull(val, "filePath must not be null");
            return this;
        }

        public Builder filePath(String val) {
            return filePath(Paths.get(val));
        }

        public Builder delimiter(char val) {
            this.delimiter = val;
            return this;
        }

        public Builder hasHeaderRow(boolean val) {
            this.hasHeaderRow = val;
            return this;
        }

        @Override
        public CsvDataSourceConnector build() {
            Objects.requireNonNull(filePath, "filePath is required for CsvDataSourceConnector");
            return new CsvDataSourceConnector(this);
        }
    }
}