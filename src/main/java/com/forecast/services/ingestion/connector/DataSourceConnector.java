package com.forecast.services.ingestion.connector;

import com.forecast.models.RawSalesData;
import com.forecast.models.exceptions.ErrorCode;
import com.forecast.models.exceptions.ForecastingException;
import com.forecast.models.interfaces.IDataConnector;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base connector that holds all shared configuration and
 * implements the retry / exponential-backoff logic defined in the spec
 * (DATA_SOURCE_UNAVAILABLE: "retry with exponential backoff, 3 attempts").
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                     Builder pattern                             │
 * │                                                                 │
 * │  DataSourceConnector is constructed exclusively through its     │
 * │  nested Builder.  Concrete subclasses (DbDataSourceConnector,  │
 * │  CsvDataSourceConnector) extend this class and add their own   │
 * │  Builder that delegates shared fields here, then adds source-  │
 * │  specific fields on top.                                        │
 * │                                                                 │
 * │  Caller usage:                                                  │
 * │    DataSourceConnector conn = new DbDataSourceConnector         │
 * │        .Builder()                                               │
 * │        .jdbcUrl("jdbc:postgresql://host:5432/forecast")         │
 * │        .credentials("user", "secret")                          │
 * │        .retryPolicy(new RetryPolicy.Builder()                   │
 * │            .maxAttempts(3).initialDelayMs(500).build())         │
 * │        .connectionPoolSize(10)                                  │
 * │        .connectionTimeoutMs(5_000)                              │
 * │        .build();                                                │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * GRASP — Information Expert: this class owns retry state because it
 * holds the RetryPolicy; it knows when to stop.
 *
 * SOLID — SRP: only responsible for connection lifecycle and retry.
 *         OCP: new connector types extend without modifying this class.
 *         DIP: callers hold IDataConnector, never this concrete type.
 */
public abstract class DataSourceConnector implements IDataConnector {

    private static final Logger LOG = Logger.getLogger(
        DataSourceConnector.class.getName()
    );

    // ── Shared configuration fields ─────────────────────────────────
    protected final RetryPolicy retryPolicy;
    protected final int connectionPoolSize;
    protected final long connectionTimeoutMs;
    protected final long socketTimeoutMs;
    protected final String sourceName; // for logging

    // ── Runtime state ───────────────────────────────────────────────
    private volatile boolean connected = false;

    // ── Constructor (package-private: only subclass Builders call it) ──
    protected DataSourceConnector(AbstractBuilder<?> builder) {
        this.retryPolicy = builder.retryPolicy;
        this.connectionPoolSize = builder.connectionPoolSize;
        this.connectionTimeoutMs = builder.connectionTimeoutMs;
        this.socketTimeoutMs = builder.socketTimeoutMs;
        this.sourceName = builder.sourceName;
    }

    // ── IDataConnector — public API ─────────────────────────────────

    /**
     * Opens the connection with exponential-backoff retry.
     * Throws ForecastingException(DATA_SOURCE_UNAVAILABLE) if all
     * attempts are exhausted.
     */
    @Override
    public final void connect() {
        int attempt = 0;
        Throwable lastCause = null;

        while (attempt < retryPolicy.getMaxAttempts()) {
            try {
                LOG.info(
                    "Connecting to [" +
                        sourceName +
                        "] — attempt " +
                        (attempt + 1) +
                        "/" +
                        retryPolicy.getMaxAttempts()
                );
                openConnection();
                connected = true;
                LOG.info("Connected to [" + sourceName + "] successfully.");
                return;
            } catch (Exception ex) {
                lastCause = ex;
                LOG.log(
                    Level.WARNING,
                    "Connection attempt " +
                        (attempt + 1) +
                        " failed for [" +
                        sourceName +
                        "]: " +
                        ex.getMessage()
                );

                if (attempt + 1 < retryPolicy.getMaxAttempts()) {
                    long delay = retryPolicy.delayForAttempt(attempt);
                    LOG.info("Retrying in " + delay + " ms …");
                    sleep(delay);
                }
                attempt++;
            }
        }

        // All attempts exhausted → MAJOR exception, halt ingestion
        LOG.severe(
            "All " +
                retryPolicy.getMaxAttempts() +
                " connection attempts to [" +
                sourceName +
                "] failed. Halting ingestion."
        );
        throw new ForecastingException(
            ErrorCode.DATA_SOURCE_UNAVAILABLE,
            "source=" +
                sourceName +
                ", attempts=" +
                retryPolicy.getMaxAttempts(),
            lastCause
        );
    }

    @Override
    public final boolean isConnected() {
        return connected;
    }

    @Override
    public final void disconnect() {
        if (connected) {
            try {
                closeConnection();
            } catch (Exception ex) {
                LOG.log(
                    Level.WARNING,
                    "Error during disconnect from [" + sourceName + "]",
                    ex
                );
            } finally {
                connected = false;
                LOG.info("Disconnected from [" + sourceName + "].");
            }
        }
    }

    /**
     * Fetches data with retry guard — ensures connect() was called first.
     */
    @Override
    public final List<RawSalesData> fetchSalesData() {
        if (!connected) {
            throw new IllegalStateException(
                "connect() must be called before fetchSalesData() on [" +
                    sourceName +
                    "]"
            );
        }
        LOG.info("Fetching sales data from [" + sourceName + "] …");
        return doFetchSalesData();
    }

    // ── Abstract hook methods for subclasses ────────────────────────

    /**
     * Perform the actual low-level connection (JDBC getConnection(), open file, etc.).
     * Called inside the retry loop — must throw if the connection fails.
     */
    protected abstract void openConnection() throws Exception;

    /**
     * Perform the actual data fetch after a successful openConnection().
     */
    protected abstract List<RawSalesData> doFetchSalesData();

    /**
     * Release the underlying resource (return connection to pool, close reader, etc.).
     */
    protected abstract void closeConnection() throws Exception;

    // ── Helpers ─────────────────────────────────────────────────────

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ForecastingException(
                ErrorCode.DATA_SOURCE_UNAVAILABLE,
                "Retry sleep interrupted for [" + sourceName + "]",
                ie
            );
        }
    }

    // ── Shared Builder base ─────────────────────────────────────────

    /**
     * Generic Builder base so DbDataSourceConnector.Builder and
     * CsvDataSourceConnector.Builder can share these fields without
     * repeating themselves.
     *
     * The type parameter T is the concrete Builder type, enabling the
     * fluent "return this" pattern to work correctly through inheritance
     * (the Curiously Recurring Template Pattern for builders in Java).
     */
    @SuppressWarnings("unchecked")
    public abstract static class AbstractBuilder<T extends AbstractBuilder<T>> {

        // Defaults match the spec: 3 attempts, sensible timeouts
        private RetryPolicy retryPolicy = RetryPolicy.DEFAULT;
        private int connectionPoolSize = 5;
        private long connectionTimeoutMs = 5_000;
        private long socketTimeoutMs = 30_000;
        private String sourceName = "unnamed-source";

        public T retryPolicy(RetryPolicy val) {
            this.retryPolicy = Objects.requireNonNull(
                val,
                "retryPolicy must not be null"
            );
            return (T) this;
        }

        public T connectionPoolSize(int val) {
            if (val < 1) throw new IllegalArgumentException(
                "connectionPoolSize must be >= 1"
            );
            this.connectionPoolSize = val;
            return (T) this;
        }

        public T connectionTimeoutMs(long val) {
            if (val < 0) throw new IllegalArgumentException(
                "connectionTimeoutMs must be >= 0"
            );
            this.connectionTimeoutMs = val;
            return (T) this;
        }

        public T socketTimeoutMs(long val) {
            if (val < 0) throw new IllegalArgumentException(
                "socketTimeoutMs must be >= 0"
            );
            this.socketTimeoutMs = val;
            return (T) this;
        }

        public T sourceName(String val) {
            this.sourceName = Objects.requireNonNull(
                val,
                "sourceName must not be null"
            );
            return (T) this;
        }

        public abstract DataSourceConnector build();
    }
}
