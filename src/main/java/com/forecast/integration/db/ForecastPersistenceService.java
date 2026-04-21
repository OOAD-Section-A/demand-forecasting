package com.forecast.integration.db;

import com.forecast.models.ForecastResult;
import com.jackfruit.scm.database.model.DemandForecast;
import com.jackfruit.scm.database.model.ForecastTimeseries;
import com.jackfruit.scm.database.model.DemandForecastingModels.ForecastPerformanceMetric;

import java.math.BigDecimal;
import java.util.ArrayList;
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
            // 1. Save forecast summary
            DemandForecast dbForecast = DemandForecastMapper.toDbForecast(result);
            adapter.createForecast(dbForecast);

            // 2. Save performance metrics
            saveForecastPerformanceMetric(result, dbForecast.getForecastId());

            // 3. Save detailed time-series points
            saveForecastTimeSeries(result, dbForecast.getForecastId());

            LOG.info(
                "Forecast, performance metrics, and time-series saved successfully for product=" +
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

    public void deleteForecastResult(String forecastId) {
        if (forecastId == null || forecastId.trim().isEmpty()) {
            throw new IllegalArgumentException("forecastId must not be blank");
        }

        try {
            adapter.deleteForecast(forecastId);
            LOG.info("Forecast deleted successfully for forecastId=" + forecastId);
        } catch (Exception ex) {
            LOG.severe(
                "Failed to delete forecast for forecastId=" +
                    forecastId +
                    " error=" + ex.getMessage()
            );
            throw new RuntimeException(ex);
        }
    }

    private void saveForecastPerformanceMetric(ForecastResult result, String forecastId) {
        ForecastPerformanceMetric metric = new ForecastPerformanceMetric(
            "EVAL-" + UUID.randomUUID(),
            forecastId,
            result.getForecastStartDate(),
            getPredictedQty(result),
            null, // actual_qty not available yet
            result.getMape(),
            result.getRmse(),
            result.getModelUsed()
        );

        adapter.createForecastPerformanceMetric(metric);

        LOG.info("Performance metric saved for forecastId=" + forecastId);
    }

    private void saveForecastTimeSeries(ForecastResult result, String forecastId) {
        List<BigDecimal> values = result.getForecastedDemand();
        List<BigDecimal> lower = result.getConfidenceIntervalLower();
        List<BigDecimal> upper = result.getConfidenceIntervalUpper();

        if (values == null || values.isEmpty()) {
            LOG.warning("No forecast time-series data to persist.");
            return;
        }

        List<ForecastTimeseries> timeseriesList = new ArrayList<>();

        for (int i = 0; i < values.size(); i++) {
            BigDecimal lowerVal =
                (lower != null && i < lower.size()) ? lower.get(i) : null;

            BigDecimal upperVal =
                (upper != null && i < upper.size()) ? upper.get(i) : null;

            ForecastTimeseries ts = new ForecastTimeseries(
                "TS-" + UUID.randomUUID(),
                forecastId,
                i + 1,
                values.get(i),
                lowerVal,
                upperVal
            );

            timeseriesList.add(ts);
        }

        adapter.createBatchForecastTimeseries(timeseriesList);

        LOG.info("Time-series forecast data saved for forecastId=" + forecastId);
    }

    private int getPredictedQty(ForecastResult result) {
        if (result.getForecastedDemand() == null || result.getForecastedDemand().isEmpty()) {
            return 0;
        }

        return result.getForecastedDemand()
            .get(0)
            .intValue();
    }
}
