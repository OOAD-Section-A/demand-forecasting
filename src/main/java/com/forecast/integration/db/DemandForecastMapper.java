package com.forecast.integration.db;

import com.forecast.models.ForecastResult;
import com.jackfruit.scm.database.model.DemandForecast;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class DemandForecastMapper {

    private DemandForecastMapper() {}

    public static DemandForecast toDbForecast(ForecastResult result) {
        DemandForecast forecast = new DemandForecast();

        forecast.setForecastId("FC-" + UUID.randomUUID());
        forecast.setProductId(result.getProductId());

        forecast.setForecastPeriod("MONTHLY");
        forecast.setForecastDate(result.getForecastStartDate());

        forecast.setPredictedDemand(sumForecast(result));
        forecast.setConfidenceScore(defaultConfidence(result));

        forecast.setReorderSignal(false);
        forecast.setSuggestedOrderQty(null);

        forecast.setLifecycleStage(result.getLifecycleStage());
        forecast.setAlgorithmUsed(result.getModelUsed());
        forecast.setGeneratedAt(LocalDateTime.now());
        forecast.setSourceEventReference("demand-forecasting-subsystem");

        return forecast;
    }

    private static int sumForecast(ForecastResult result) {
        List<BigDecimal> values = result.getForecastedDemand();
        if (values == null || values.isEmpty()) return 0;

        return values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .intValue();
    }

    private static BigDecimal defaultConfidence(ForecastResult result) {
        if (result.getMape() == null) {
            return new BigDecimal("80.00");
        }

        BigDecimal hundred = new BigDecimal("100.00");
        BigDecimal confidence = hundred.subtract(result.getMape());

        if (confidence.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (confidence.compareTo(hundred) > 0) {
            return hundred;
        }

        return confidence;
    }
}