package com.forecast.ingestion.connector;

import com.forecast.domain.RawSalesData;
import com.forecast.exception.ErrorCode;
import com.forecast.exception.ForecastingException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the DataSourceConnector Builder hierarchy,
 * RetryPolicy, and CsvDataSourceConnector parsing.
 *
 * DbDataSourceConnector tests are integration tests (require a live DB)
 * and live in src/test/.../integration/DbConnectorIT.java.
 */
class DataSourceConnectorTest {

    // ================================================================
    // RetryPolicy Builder
    // ================================================================

    @Nested
    @DisplayName("RetryPolicy.Builder")
    class RetryPolicyBuilderTest {

        @Test
        @DisplayName("defaults match the spec (3 attempts, 500 ms initial)")
        void defaultValues() {
            RetryPolicy p = new RetryPolicy.Builder().build();
            assertEquals(3,     p.getMaxAttempts());
            assertEquals(500L,  p.getInitialDelayMs());
            assertEquals(8_000L, p.getMaxDelayMs());
        }

        @Test
        @DisplayName("exponential backoff is capped at maxDelayMs")
        void backoffCap() {
            RetryPolicy p = new RetryPolicy.Builder()
                    .initialDelayMs(1_000)
                    .maxDelayMs(3_000)
                    .build();

            assertEquals(1_000L, p.delayForAttempt(0));  // 1000 * 2^0
            assertEquals(2_000L, p.delayForAttempt(1));  // 1000 * 2^1
            assertEquals(3_000L, p.delayForAttempt(2));  // 1000 * 2^2 = 4000, capped to 3000
        }

        @Test
        @DisplayName("maxAttempts < 1 is rejected")
        void maxAttemptsValidation() {
            assertThrows(IllegalArgumentException.class,
                    () -> new RetryPolicy.Builder().maxAttempts(0).build());
        }
    }

    // ================================================================
    // CsvDataSourceConnector Builder
    // ================================================================

    @Nested
    @DisplayName("CsvDataSourceConnector.Builder")
    class CsvBuilderTest {

        @Test
        @DisplayName("build() fails when filePath is not set")
        void requiresFilePath() {
            assertThrows(NullPointerException.class,
                    () -> new CsvDataSourceConnector.Builder().build());
        }

        @Test
        @DisplayName("fluent builder sets all fields correctly")
        void fluentBuilder(@TempDir Path tmp) throws IOException {
            Path csv = tmp.resolve("sales.csv");
            Files.writeString(csv, ""); // empty file

            CsvDataSourceConnector conn = new CsvDataSourceConnector.Builder()
                    .filePath(csv)
                    .delimiter(';')
                    .hasHeaderRow(false)
                    .retryPolicy(RetryPolicy.NO_RETRY)
                    .connectionPoolSize(1)
                    .connectionTimeoutMs(2_000)
                    .sourceName("test-csv")
                    .build();

            assertNotNull(conn);
            assertFalse(conn.isConnected());
        }
    }

    // ================================================================
    // CsvDataSourceConnector — parsing
    // ================================================================

    @Nested
    @DisplayName("CsvDataSourceConnector — data parsing")
    class CsvParsingTest {

        private static final String HEADER =
                "sale_id,product_id,store_id,sale_date,quantity_sold,unit_price,revenue,region";

        @Test
        @DisplayName("parses a well-formed CSV correctly")
        void happyPath(@TempDir Path tmp) throws IOException {
            Path csv = writeLines(tmp, "sales.csv",
                    HEADER,
                    "1001,PROD-01,STORE-A,2024-12-25,15,29.99,449.85,SOUTH",
                    "1002,PROD-02,STORE-B,2024-12-26,8,49.99,399.92,NORTH"
            );

            List<RawSalesData> rows = connectAndFetch(csv);

            assertEquals(2, rows.size());

            RawSalesData first = rows.get(0);
            assertEquals(1001L,                        first.getSaleId());
            assertEquals("PROD-01",                    first.getProductId());
            assertEquals("STORE-A",                    first.getStoreId());
            assertEquals(LocalDate.of(2024, 12, 25),   first.getSaleDate());
            assertEquals(15,                           first.getQuantitySold());
            assertEquals(new BigDecimal("29.99"),      first.getUnitPrice());
            assertEquals(new BigDecimal("449.85"),     first.getRevenue());
            assertEquals("SOUTH",                     first.getRegion());
        }

        @Test
        @DisplayName("skips malformed rows and continues (does not throw)")
        void skipsInvalidRows(@TempDir Path tmp) throws IOException {
            Path csv = writeLines(tmp, "bad.csv",
                    HEADER,
                    "1001,PROD-01,STORE-A,2024-12-25,15,29.99,449.85,SOUTH", // valid
                    "GARBAGE",                                                 // too few columns
                    "1003,PROD-03,STORE-C,NOT-A-DATE,5,9.99,49.95,EAST"      // bad date
            );

            List<RawSalesData> rows = connectAndFetch(csv);

            // Only the valid row is retained
            assertEquals(1, rows.size());
            assertEquals("PROD-01", rows.get(0).getProductId());
        }

        @Test
        @DisplayName("returns empty list for file with only header")
        void emptyFile(@TempDir Path tmp) throws IOException {
            Path csv = writeLines(tmp, "empty.csv", HEADER);
            List<RawSalesData> rows = connectAndFetch(csv);
            assertTrue(rows.isEmpty());
        }

        @Test
        @DisplayName("throws DATA_SOURCE_UNAVAILABLE when file does not exist")
        void missingFile(@TempDir Path tmp) {
            Path missing = tmp.resolve("nonexistent.csv");

            CsvDataSourceConnector conn = new CsvDataSourceConnector.Builder()
                    .filePath(missing)
                    .retryPolicy(RetryPolicy.NO_RETRY)
                    .sourceName("missing-csv")
                    .build();

            ForecastingException ex = assertThrows(ForecastingException.class, conn::connect);
            assertEquals(ErrorCode.DATA_SOURCE_UNAVAILABLE, ex.getErrorCode());
        }

        @Test
        @DisplayName("fetchSalesData() throws IllegalStateException before connect()")
        void fetchBeforeConnect(@TempDir Path tmp) throws IOException {
            Path csv = writeLines(tmp, "s.csv", HEADER);
            CsvDataSourceConnector conn = new CsvDataSourceConnector.Builder()
                    .filePath(csv).build();

            assertThrows(IllegalStateException.class, conn::fetchSalesData);
        }

        // ── Helpers ─────────────────────────────────────────────────

        private Path writeLines(Path dir, String name, String... lines) throws IOException {
            Path f = dir.resolve(name);
            Files.write(f, List.of(lines));
            return f;
        }

        private List<RawSalesData> connectAndFetch(Path csv) {
            CsvDataSourceConnector conn = new CsvDataSourceConnector.Builder()
                    .filePath(csv)
                    .retryPolicy(RetryPolicy.NO_RETRY)
                    .sourceName("test-csv")
                    .build();
            conn.connect();
            try {
                return conn.fetchSalesData();
            } finally {
                conn.disconnect();
            }
        }
    }

    // ================================================================
    // DbDataSourceConnector Builder (construction only — no live DB)
    // ================================================================

    @Nested
    @DisplayName("DbDataSourceConnector.Builder")
    class DbBuilderTest {

        @Test
        @DisplayName("build() fails when jdbcUrl is not set")
        void requiresJdbcUrl() {
            assertThrows(NullPointerException.class,
                    () -> new DbDataSourceConnector.Builder().build());
        }

        @Test
        @DisplayName("fluent builder produces a disconnected connector")
        void fluentBuilder() {
            DbDataSourceConnector conn = new DbDataSourceConnector.Builder()
                    .jdbcUrl("jdbc:postgresql://localhost:5432/forecast")
                    .credentials("user", "pass")
                    .schema("public")
                    .salesTable("sales_records")
                    .connectionPoolSize(10)
                    .connectionTimeoutMs(5_000)
                    .socketTimeoutMs(30_000)
                    .retryPolicy(new RetryPolicy.Builder()
                            .maxAttempts(3)
                            .initialDelayMs(500)
                            .build())
                    .sourceName("prod-db")
                    .build();

            assertNotNull(conn);
            assertFalse(conn.isConnected());
        }
    }
}