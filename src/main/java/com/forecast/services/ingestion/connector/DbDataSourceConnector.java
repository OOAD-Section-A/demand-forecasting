package com.forecast.services.ingestion.connector;

import com.forecast.integration.db.DemandForecastingDbAdapter;
import com.forecast.models.RawSalesData;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.DemandForecastingModels.SalesRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Database-backed DataSourceConnector.
 *
 * Uses the shared database module instead of direct JDBC. The database module
 * reads runtime connection settings from JVM system properties, environment
 * variables, or database.properties.
 */
public final class DbDataSourceConnector extends DataSourceConnector {

    private static final Logger LOG = Logger.getLogger(
        DbDataSourceConnector.class.getName()
    );

    private SupplyChainDatabaseFacade facade;
    private DemandForecastingDbAdapter adapter;

    private DbDataSourceConnector(Builder b) {
        super(b);
    }

    @Override
    protected void openConnection() {
        facade = new SupplyChainDatabaseFacade();
        adapter = new DemandForecastingDbAdapter(facade);
        LOG.fine("Database module facade opened for demand forecasting ingestion.");
    }

    @Override
    protected List<RawSalesData> doFetchSalesData() {
        List<RawSalesData> results = new ArrayList<>();

        for (SalesRecord record : adapter.listSalesRecords()) {
            results.add(mapRecord(record));
        }

        LOG.info("Fetched " + results.size() + " sales rows from database module.");
        return Collections.unmodifiableList(results);
    }

    @Override
    protected void closeConnection() {
        if (facade != null) {
            facade.close();
        }
    }

    private RawSalesData mapRecord(SalesRecord record) {
        return new RawSalesData.Builder()
            .saleId(toLongId(record.saleId()))
            .productId(record.productId())
            .storeId(record.storeId())
            .saleDate(record.saleDate())
            .quantitySold(record.quantitySold())
            .unitPrice(record.unitPrice())
            .revenue(record.revenue())
            .region(record.region())
            .build();
    }

    private long toLongId(String saleId) {
        if (saleId == null || saleId.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(saleId);
        } catch (NumberFormatException ex) {
            return Math.abs((long) saleId.hashCode());
        }
    }

    public static final class Builder extends AbstractBuilder<Builder> {

        /**
         * Kept for source compatibility. The database module reads db.url from
         * JVM system properties, environment variables, or database.properties.
         */
        public Builder jdbcUrl(String val) {
            Objects.requireNonNull(val, "jdbcUrl must not be null");
            return this;
        }

        /**
         * Kept for source compatibility. Use -Ddb.username and -Ddb.password at runtime.
         */
        public Builder credentials(String username, String password) {
            Objects.requireNonNull(username, "username must not be null");
            Objects.requireNonNull(password, "password must not be null");
            return this;
        }

        /**
         * Kept for source compatibility. Schema selection is owned by the database module.
         */
        public Builder schema(String val) {
            Objects.requireNonNull(val, "schema must not be null");
            return this;
        }

        /**
         * Kept for source compatibility. Table access is owned by the database module.
         */
        public Builder salesTable(String val) {
            Objects.requireNonNull(val, "salesTable must not be null");
            return this;
        }

        @Override
        public DbDataSourceConnector build() {
            return new DbDataSourceConnector(this);
        }
    }
}
