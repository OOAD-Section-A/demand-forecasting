package com.forecast.integration.db;

import com.forecast.models.ForecastResult;
import com.jackfruit.scm.database.model.DemandForecast;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

public class ForecastPersistenceService {

    private static final Logger LOG = Logger.getLogger(
        ForecastPersistenceService.class.getName()
    );

    private final DemandForecastingDbAdapter adapter;

    public ForecastPersistenceService(DemandForecastingDbAdapter adapter) {
        this.adapter = Objects.requireNonNull(
            adapter,
            "DemandForecastingDbAdapter must not be null"
        );
    }

    public void saveForecastResult(ForecastResult result) {
        Objects.requireNonNull(result, "ForecastResult must not be null");

        try {
            DemandForecast dbForecast = DemandForecastMapper.toDbForecast(result);
            adapter.createForecast(dbForecast);

            saveForecastTimeSeries(result, dbForecast.getForecastId());

            LOG.info(
                "Forecast saved to DB successfully for product=" +
                    result.getProductId()
            );

        } catch (Exception ex) {
            LOG.severe(
                "Failed to persist forecast for product=" +
                    result.getProductId() +
                    " error=" + ex.getMessage()
            );
            throw new RuntimeException(ex);
        }
    }

    private void saveForecastTimeSeries(ForecastResult result, String forecastId) throws Exception {
        List<BigDecimal> values = result.getForecastedDemand();
        List<BigDecimal> lower = result.getConfidenceIntervalLower();
        List<BigDecimal> upper = result.getConfidenceIntervalUpper();

        if (values == null || values.isEmpty()) {
            LOG.warning("No forecast time-series data to persist.");
            return;
        }

        Connection conn = DriverManager.getConnection(
            "jdbc:mysql://localhost:3306/demandforecastinglocal",
            "root",
            "anurag10"
        );

        String sql =
            "INSERT INTO forecast_timeseries " +
            "(id, forecast_id, time_index, forecast_value, lower_bound, upper_bound) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

        PreparedStatement stmt = conn.prepareStatement(sql);

        try {
            for (int i = 0; i < values.size(); i++) {
                String id = "TS-" + UUID.randomUUID();

                BigDecimal lowerVal =
                    (lower != null && i < lower.size()) ? lower.get(i) : null;

                BigDecimal upperVal =
                    (upper != null && i < upper.size()) ? upper.get(i) : null;

                stmt.setString(1, id);
                stmt.setString(2, forecastId);
                stmt.setInt(3, i + 1);
                stmt.setBigDecimal(4, values.get(i));

                if (lowerVal != null) {
                    stmt.setBigDecimal(5, lowerVal);
                } else {
                    stmt.setNull(5, java.sql.Types.DECIMAL);
                }

                if (upperVal != null) {
                    stmt.setBigDecimal(6, upperVal);
                } else {
                    stmt.setNull(6, java.sql.Types.DECIMAL);
                }

                stmt.executeUpdate();
            }
        } finally {
            stmt.close();
            conn.close();
        }

        LOG.info("Time-series forecast data saved for forecastId=" + forecastId);
    }
}