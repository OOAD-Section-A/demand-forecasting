package com.forecast.services.output;

import com.forecast.integration.db.ForecastPersistenceService;
import com.forecast.integration.inventory.IInventoryAdapter;
import com.forecast.models.ForecastResult;
import com.forecast.models.exceptions.ErrorCode;
import com.forecast.models.exceptions.ForecastingException;
import com.forecast.models.exceptions.IMLAlgorithmicExceptionSource;
import com.forecast.models.inventory.ReplenishmentRecommendation;
import com.forecast.services.inventory.InventoryIntegrationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ForecastOutputService — Responsible for persisting, publishing forecast results,
 * and integrating forecasts with inventory management.
 *
 * Responsibilities:
 * - Persist forecast results to the database
 * - Publish forecasts to downstream systems
 * - Generate inventory replenishment recommendations based on forecasts
 * - Monitor forecast quality and alert on anomalies
 * - Handle integration with inventory management subsystem
 *
 * @author Demand Forecasting Team
 * @version 2.0
 */
public class ForecastOutputService {

    private static final Logger LOG = Logger.getLogger(
        ForecastOutputService.class.getName()
    );

    private static final double ALERT_LIFT_THRESHOLD = 3.0;
    private static final int DEFAULT_LEAD_TIME_DAYS = 7;

    private final IMLAlgorithmicExceptionSource exceptionSource;
    private final ForecastPersistenceService persistenceService;
    private final InventoryIntegrationService inventoryIntegrationService;
    private boolean inventoryIntegrationEnabled;

    /**
     * Constructor with inventory integration support.
     *
     * @param exceptionSource the exception source for alerting
     * @param persistenceService the persistence service for forecast storage
     * @param inventoryIntegrationService the service for inventory integration
     */
    public ForecastOutputService(
        IMLAlgorithmicExceptionSource exceptionSource,
        ForecastPersistenceService persistenceService,
        InventoryIntegrationService inventoryIntegrationService
    ) {
        this.exceptionSource = Objects.requireNonNull(
            exceptionSource,
            "exceptionSource must not be null"
        );
        this.persistenceService = Objects.requireNonNull(
            persistenceService,
            "persistenceService must not be null"
        );
        this.inventoryIntegrationService = inventoryIntegrationService;
        this.inventoryIntegrationEnabled = inventoryIntegrationService != null;

        LOG.info(
            "ForecastOutputService initialised with inventory integration: " +
                inventoryIntegrationEnabled
        );
    }

    /**
     * Constructor without inventory integration (backward compatibility).
     *
     * @param exceptionSource the exception source for alerting
     * @param persistenceService the persistence service for forecast storage
     */
    public ForecastOutputService(
        IMLAlgorithmicExceptionSource exceptionSource,
        ForecastPersistenceService persistenceService
    ) {
        this(exceptionSource, persistenceService, null);
    }

    /**
     * Publishes a forecast result, including persistence, quality checks,
     * inventory integration, and alert generation.
     *
     * @param result the forecast result to publish
     */
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

        // Quality checks and alerts
        checkForAlerts(result);

        // Persist the forecast
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

        // Generate replenishment signal and recommendations
        generateReplenishmentSignal(result);

        // Integrate with inventory management
        if (inventoryIntegrationEnabled) {
            generateInventoryRecommendations(result);
        }
    }

    /**
     * Publishes a degraded forecast (when primary models fail).
     *
     * @param result the degraded forecast result
     */
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

    /**
     * Generates a replenishment signal based on forecasted demand.
     * This is called after forecast persistence and provides the basis for
     * inventory replenishment operations.
     *
     * @param result the forecast result
     */
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
                "] with demand forecast from " +
                result.getForecastStartDate() +
                " to " +
                result.getForecastEndDate()
        );
    }

    /**
     * Generates inventory replenishment recommendations based on the forecast result.
     * This method integrates with the inventory management subsystem to produce
     * actionable recommendations for stock replenishment.
     *
     * @param forecastResult the forecast result
     */
    public void generateInventoryRecommendations(
        ForecastResult forecastResult
    ) {
        if (inventoryIntegrationService == null) {
            LOG.fine("Inventory integration service not available");
            return;
        }

        try {
            LOG.info(
                "Generating inventory recommendations for product=[" +
                    forecastResult.getProductId() +
                    "] store=[" +
                    forecastResult.getStoreId() +
                    "]"
            );

            String storeId = forecastResult.getStoreId();
            if (storeId == null || storeId.isEmpty()) {
                LOG.warning(
                    "Store ID is missing from forecast result; cannot generate inventory recommendations"
                );
                exceptionSource.fireMissingInputData(
                    558,
                    "ForecastOutputService.generateInventoryRecommendations",
                    "StoreId",
                    "Forecast result does not contain store identifier"
                );
                return;
            }

            // Create location list (typically just the store ID)
            List<String> locationIds = new ArrayList<>();
            locationIds.add(storeId);

            // Generate recommendations through inventory integration service
            List<ReplenishmentRecommendation> recommendations =
                inventoryIntegrationService.processForecasterGenerateRecommendations(
                    forecastResult,
                    locationIds
                );

            if (recommendations.isEmpty()) {
                LOG.warning(
                    "No inventory recommendations generated for product=[" +
                        forecastResult.getProductId() +
                        "]"
                );
                return;
            }

            // Log generated recommendations
            for (ReplenishmentRecommendation recommendation : recommendations) {
                LOG.info(
                    "Generated recommendation: product=[" +
                        recommendation.getProductId() +
                        "], location=[" +
                        recommendation.getLocationId() +
                        "], quantity=[" +
                        recommendation.getRecommendedOrderQuantity() +
                        "], urgency=[" +
                        recommendation.getUrgencyLevel() +
                        "]"
                );

                // Alert on critical recommendations
                if ("CRITICAL".equals(recommendation.getUrgencyLevel())) {
                    alertCriticalInventoryCondition(recommendation);
                }
            }

            LOG.info(
                "Successfully generated " +
                    recommendations.size() +
                    " inventory recommendations for product=[" +
                    forecastResult.getProductId() +
                    "]"
            );
        } catch (Exception e) {
            LOG.log(
                Level.SEVERE,
                "Failed to generate inventory recommendations for product=[" +
                    forecastResult.getProductId() +
                    "]",
                e
            );

            exceptionSource.fireAlgorithmicAlert(
                559,
                "ForecastOutputService.generateInventoryRecommendations",
                forecastResult.getProductId() +
                    "/" +
                    forecastResult.getStoreId(),
                "Failed to generate inventory recommendations: " +
                    e.getMessage()
            );
        }
    }

    /**
     * Checks forecast results for quality issues and generates alerts.
     * Monitors for demand spikes, unusual patterns, and forecast anomalies.
     *
     * @param result the forecast result to check
     */
    private void checkForAlerts(ForecastResult result) {
        if (
            result.getForecastedDemand() == null ||
            result.getForecastedDemand().isEmpty()
        ) {
            return;
        }

        // Calculate demand statistics
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

        double min = result
            .getForecastedDemand()
            .stream()
            .mapToDouble(v -> v.doubleValue())
            .min()
            .orElse(0.0);

        // Alert on extreme demand spikes
        if (avg > 0 && (max / avg) > ALERT_LIFT_THRESHOLD) {
            exceptionSource.fireAlgorithmicAlert(
                463,
                "ForecastOutputService",
                result.getProductId() + "/" + result.getStoreId(),
                "Extreme demand spike detected: min=" +
                    String.format("%.2f", min) +
                    ", avg=" +
                    String.format("%.2f", avg) +
                    ", max=" +
                    String.format("%.2f", max) +
                    ", ratio=" +
                    String.format("%.2f", max / avg)
            );
        }

        // Alert on zero or very low demand forecast
        if (avg <= 0) {
            LOG.warning(
                "Zero or negative average demand forecasted for product=[" +
                    result.getProductId() +
                    "]"
            );
        }
    }

    /**
     * Alerts on critical inventory conditions detected during recommendation generation.
     *
     * @param recommendation the critical replenishment recommendation
     */
    private void alertCriticalInventoryCondition(
        ReplenishmentRecommendation recommendation
    ) {
        LOG.severe(
            "CRITICAL INVENTORY CONDITION DETECTED: product=[" +
                recommendation.getProductId() +
                "], location=[" +
                recommendation.getLocationId() +
                "], current stock=[" +
                recommendation.getCurrentStock() +
                "], safety stock=[" +
                recommendation.getSafetyStockLevel() +
                "]"
        );

        exceptionSource.fireAlgorithmicAlert(
            560,
            "ForecastOutputService.alertCriticalInventoryCondition",
            recommendation.getProductId() +
                "/" +
                recommendation.getLocationId(),
            "CRITICAL: Current stock (" +
                recommendation.getCurrentStock() +
                ") below safety level (" +
                recommendation.getSafetyStockLevel() +
                "). Immediate replenishment required. Recommended order: " +
                recommendation.getRecommendedOrderQuantity() +
                " units."
        );
    }

    /**
     * Enables or disables inventory integration.
     *
     * @param enabled true to enable inventory integration, false to disable
     */
    public void setInventoryIntegrationEnabled(boolean enabled) {
        this.inventoryIntegrationEnabled = enabled;
        LOG.info("Inventory integration enabled: " + enabled);
    }

    /**
     * Checks if inventory integration is enabled.
     *
     * @return true if inventory integration is enabled, false otherwise
     */
    public boolean isInventoryIntegrationEnabled() {
        return inventoryIntegrationEnabled;
    }
}
