package com.forecast.services.output;

import com.forecast.integration.db.ForecastPersistenceService;
import com.forecast.models.ForecastResult;
import com.forecast.models.exceptions.ErrorCode;
import com.forecast.models.exceptions.ForecastingException;
import com.forecast.models.exceptions.IMLAlgorithmicExceptionSource;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * ForecastOutputService — Responsible for persisting and publishing forecast results.
 */
public class ForecastOutputService {

    private static final Logger LOG = Logger.getLogger(
        ForecastOutputService.class.getName()
    );
    private static final double ALERT_LIFT_THRESHOLD = 3.0;

    private final IMLAlgorithmicExceptionSource exceptionSource;
    private final ForecastPersistenceService persistenceService;

    public ForecastOutputService(
        IMLAlgorithmicExceptionSource exceptionSource,
        ForecastPersistenceService persistenceService
    ) {
        this.exceptionSource = Objects.requireNonNull(
            exceptionSource,
            "exceptionSource must not be null"
        );
        this.persistenceService = Objects.requireNonNull(
            persistenceService,
            "persistenceService must not be null"
        );

        LOG.info("ForecastOutputService initialised.");
    }

    public void publishForecast(ForecastResult result) {
        Objects.requireNonNull(result, "ForecastResult must not be null");

        LOG.info(
            "Publishing forecast for product=[" +
                result.getProductId() +
                "] store=[" +
                result.getStoreId() +
                "] status=[" +
                result.getStatus() +
                "]"
        );

        checkForAlerts(result);

        try {
            persistenceService.saveForecastResult(result);

            LOG.info(
                "Forecast result persisted to database for product=" +
                    result.getProductId()
            );
        } catch (RuntimeException ex) {
            throw new ForecastingException(
                ErrorCode.DB_WRITE_FAILURE,
                "product=" +
                    result.getProductId() +
                    ", store=" +
                    result.getStoreId(),
                ex
            );
        }

        generateReplenishmentSignal(result);
    }

    public void publishDegradedForecast(ForecastResult result) {
        Objects.requireNonNull(result, "ForecastResult must not be null");
        result.setStatus("DEGRADED");

        LOG.warning(
            "Publishing DEGRADED forecast for product=[" +
                result.getProductId() +
                "]"
        );

        publishForecast(result);
    }

    public void generateReplenishmentSignal(ForecastResult result) {
        if (
            result.getForecastedDemand() == null ||
            result.getForecastedDemand().isEmpty()
        ) {
            LOG.warning(
                "Cannot generate replenishment signal — no forecasted demand values."
            );

            exceptionSource.fireMissingInputData(
                457,
                "ForecastOutputService",
                "ForecastedDemandValues",
                result.getForecastStartDate() +
                    "/" +
                    result.getForecastEndDate()
            );
            return;
        }

        LOG.info(
            "Replenishment signal generated for product=[" +
                result.getProductId() +
                "] (stub)."
        );
    }

    private void checkForAlerts(ForecastResult result) {
        if (
            result.getForecastedDemand() == null ||
            result.getForecastedDemand().isEmpty()
        ) {
            return;
        }

        double avg = result
            .getForecastedDemand()
            .stream()
            .mapToDouble(v -> v.doubleValue())
            .average()
            .orElse(0.0);

        double max = result
            .getForecastedDemand()
            .stream()
            .mapToDouble(v -> v.doubleValue())
            .max()
            .orElse(0.0);

        if (avg > 0 && (max / avg) > ALERT_LIFT_THRESHOLD) {
            exceptionSource.fireAlgorithmicAlert(
                463,
                "ForecastOutputService",
                result.getProductId() + "/" + result.getStoreId(),
                "Extreme demand spike detected: max=" +
                    max +
                    ", avg=" +
                    avg +
                    ", ratio=" +
                    String.format("%.2f", max / avg)
            );
        }
    }
}