package com.forecast.ingestion.connector;

import com.forecast.domain.RawSalesData;
import com.forecast.exception.ErrorCode;
import com.forecast.exception.ForecastingException;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Database-backed DataSourceConnector.
 *
 * Uses JDBC directly. In production you would swap the single
 * DriverManager.getConnection() call for a HikariCP / c3p0 pool
 * checkout — the pool size and timeout fields are already wired up
 * in the parent Builder for that integration.
 *
 * Construction (Builder pattern):
 *
 *   IDataConnector conn = new DbDataSourceConnector.Builder()
 *       .jdbcUrl("jdbc:postgresql://db-host:5432/forecast_db")
 *       .credentials("forecast_user", "s3cret")
 *       .schema("public")
 *       .salesTable("sales_records")
 *       .retryPolicy(new RetryPolicy.Builder()
 *           .maxAttempts(3)
 *           .initialDelayMs(500)
 *           .maxDelayMs(8_000)
 *           .build())
 *       .connectionPoolSize(10)
 *       .connectionTimeoutMs(5_000)
 *       .socketTimeoutMs(30_000)
 *       .sourceName("forecast-db-prod")
 *       .build();
 *
 *   conn.connect();
 *   List<RawSalesData> rows = conn.fetchSalesData();
 *   conn.disconnect();
 */
public final class DbDataSourceConnector extends DataSourceConnector {

    private static final Logger LOG = Logger.getLogger(DbDataSourceConnector.class.getName());

    private static final String FETCH_SQL =
            "SELECT sale_id, product_id, store_id, sale_date, " +
            "       quantity_sold, unit_price, revenue, region " +
            "FROM   %s.%s " +
            "ORDER  BY sale_date";

    // ── DB-specific config ──────────────────────────────────────────
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String schema;
    private final String salesTable;

    // ── Runtime state ───────────────────────────────────────────────
    private Connection activeConnection;

    private DbDataSourceConnector(Builder b) {
        super(b);
        this.jdbcUrl     = b.jdbcUrl;
        this.username    = b.username;
        this.password    = b.password;
        this.schema      = b.schema;
        this.salesTable  = b.salesTable;
    }

    // ── Hook implementations ────────────────────────────────────────

    @Override
    protected void openConnection() throws Exception {
        // In production: replace with pool.getConnection()
        activeConnection = DriverManager.getConnection(jdbcUrl, username, password);
        activeConnection.setNetworkTimeout(null, (int) socketTimeoutMs);
        activeConnection.setAutoCommit(false);
        LOG.fine("JDBC connection opened to " + jdbcUrl);
    }

    @Override
    protected List<RawSalesData> doFetchSalesData() {
        String sql = String.format(FETCH_SQL, schema, salesTable);
        List<RawSalesData> results = new ArrayList<>();

        try (PreparedStatement ps = activeConnection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                results.add(mapRow(rs));
            }

        } catch (SQLException ex) {
            throw new ForecastingException(
                    ErrorCode.DATA_SOURCE_UNAVAILABLE,
                    "SQL fetch failed on " + schema + "." + salesTable, ex);
        }

        LOG.info("Fetched " + results.size() + " rows from " + schema + "." + salesTable);
        return Collections.unmodifiableList(results);
    }

    @Override
    protected void closeConnection() throws Exception {
        if (activeConnection != null && !activeConnection.isClosed()) {
            activeConnection.close();
        }
    }

    // ── Row mapping ─────────────────────────────────────────────────

    private RawSalesData mapRow(ResultSet rs) throws SQLException {
        return new RawSalesData.Builder()
                .saleId(rs.getLong("sale_id"))
                .productId(rs.getString("product_id"))
                .storeId(rs.getString("store_id"))
                .saleDate(rs.getDate("sale_date").toLocalDate())
                .quantitySold(rs.getInt("quantity_sold"))
                .unitPrice(rs.getBigDecimal("unit_price"))
                .revenue(rs.getBigDecimal("revenue"))
                .region(rs.getString("region"))
                .build();
    }

    // ── Builder ─────────────────────────────────────────────────────

    public static final class Builder extends AbstractBuilder<Builder> {

        private String jdbcUrl;
        private String username    = "";
        private String password    = "";
        private String schema      = "public";
        private String salesTable  = "sales_records";

        public Builder jdbcUrl(String val) {
            this.jdbcUrl = Objects.requireNonNull(val, "jdbcUrl must not be null");
            return this;
        }

        public Builder credentials(String username, String password) {
            this.username = Objects.requireNonNull(username, "username must not be null");
            this.password = Objects.requireNonNull(password, "password must not be null");
            return this;
        }

        public Builder schema(String val) {
            this.schema = Objects.requireNonNull(val, "schema must not be null");
            return this;
        }

        public Builder salesTable(String val) {
            this.salesTable = Objects.requireNonNull(val, "salesTable must not be null");
            return this;
        }

        @Override
        public DbDataSourceConnector build() {
            Objects.requireNonNull(jdbcUrl, "jdbcUrl is required for DbDataSourceConnector");
            return new DbDataSourceConnector(this);
        }
    }
}