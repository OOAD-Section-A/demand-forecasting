package com.forecast.services.inventory;

import com.forecast.integration.inventory.IInventoryAdapter;
import com.forecast.integration.inventory.InventoryParameters;
import com.forecast.models.ForecastResult;
import com.forecast.models.inventory.ReplenishmentRecommendation;
import com.forecast.models.exceptions.ErrorCode;
import com.forecast.models.exceptions.ForecastingException;
import com.forecast.models.exceptions.IMLAlgorithmicExceptionSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * InventoryIntegrationService orchestrates the integration between the demand forecasting
 * system and the inventory management subsystem.
 *
 * This service is responsible for:
 * - Processing forecast results and generating inventory replenishment recommendations
 * - Executing replenishment orders based on recommendations
 * - Monitoring inventory levels and alerting on stock issues
 * - Maintaining audit trails of all inventory operations
 * - Handling error conditions and fallback scenarios
 *
 * The service acts as a facade that hides the complexity of inventory management,
 * providing a clean interface to the forecast system.
 *
 * @author Integration Team
 * @version 1.0
 */
public class InventoryIntegrationService {

    private static final Logger LOGGER = Logger.getLogger(
        InventoryIntegrationService.class.getName()
    );

    private static final int CRITICAL_STOCK_THRESHOLD = 10; // units
    private static final double LOW_STOCK_PERCENTAGE = 0.25; // 25% of reorder point
    private static final int RECOMMENDATION_CACHE_SIZE = 1000;

    private final IInventoryAdapter inventoryAdapter;
    private final IMLAlgorithmicExceptionSource exceptionSource;
    private final List<ReplenishmentRecommendation> recommendationHistory;

    /**
     * Constructs an InventoryIntegrationService with the provided adapter and exception source.
     *
     * @param inventoryAdapter the adapter for inventory operations
     * @param exceptionSource the exception source for alerting
     */
    public InventoryIntegrationService(
        IInventoryAdapter inventoryAdapter,
        IMLAlgorithmicExceptionSource exceptionSource
    ) {
        this.inventoryAdapter = Objects.requireNonNull(
            inventoryAdapter,
            "inventoryAdapter must not be null"
        );
        this.exceptionSource = Objects.requireNonNull(
            exceptionSource,
            "exceptionSource must not be null"
        );
        this.recommendationHistory = new ArrayList<>();
        LOGGER.info("InventoryIntegrationService initialized");
    }

    /**
     * Processes a forecast result and generates replenishment recommendations for all relevant locations.
     *
     * @param forecastResult the demand forecast result
     * @param locationIds list of location identifiers to generate recommendations for
     * @return a list of ReplenishmentRecommendations
     */
    public List<ReplenishmentRecommendation> processForecasterGenerateRecommendations(
        ForecastResult forecastResult,
        List<String> locationIds
    ) {
        Objects.requireNonNull(forecastResult, "ForecastResult must not be null");
        Objects.requireNonNull(locationIds, "locationIds must not be null");

        LOGGER.info("Processing forecast for product=[" + forecastResult.getProductId() +
            "] to generate recommendations for " + locationIds.size() + " locations");

        List<ReplenishmentRecommendation> recommendations = new ArrayList<>();

        if (!inventoryAdapter.isInventorySystemAvailable()) {
            LOGGER.warning("Inventory system is not available; cannot generate recommendations");
            exceptionSource.fireMissingInputData(
                550,
                "InventoryIntegrationService",
                "InventorySystemAvailability",
                "Inventory adapter returned unavailable status"
            );
            return recommendations;
        }

        for (String locationId : locationIds) {
            try {
                ReplenishmentRecommendation recommendation =
                    generateRecommendationForLocation(forecastResult, locationId);

                if (recommendation != null) {
                    recommendations.add(recommendation);
                    addToHistory(recommendation);

                    // Check for critical conditions
                    if ("CRITICAL".equals(recommendation.getUrgencyLevel())) {
                        alertCriticalStock(recommendation);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                    "Failed to generate recommendation for product=[" + forecastResult.getProductId() +
                        "] at location=[" + locationId + "]", e);

                exceptionSource.fireAlgorithmicAlert(
                    551,
                    "InventoryIntegrationService.processForecasterGenerateRecommendations",
                    forecastResult.getProductId() + "/" + locationId,
                    "Failed to generate replenishment recommendation: " + e.getMessage()
                );
            }
        }

        LOGGER.info("Generated " + recommendations.size() + " recommendations for product=[" +
            forecastResult.getProductId() + "]");

        return recommendations;
    }

    /**
     * Generates a replenishment recommendation for a specific product-location combination.
     *
     * @param forecastResult the demand forecast result
     * @param locationId the location identifier
     * @return a ReplenishmentRecommendation
     */
    public ReplenishmentRecommendation generateRecommendationForLocation(
        ForecastResult forecastResult,
        String locationId
    ) {
        Objects.requireNonNull(forecastResult, "ForecastResult must not be null");
        Objects.requireNonNull(locationId, "locationId must not be null");

        String productId = forecastResult.getProductId();

        LOGGER.fine("Generating recommendation for product=[" + productId +
            "] at location=[" + locationId + "]");

        try {
            // Check if product exists in inventory
            if (!inventoryAdapter.productExists(productId, locationId)) {
                LOGGER.warning("Product=[" + productId + "] does not exist at location=[" + locationId + "]");
                return null;
            }

            // Gather current inventory state
            int currentStock = inventoryAdapter.getCurrentStockLevel(productId, locationId);
            int safetyStock = inventoryAdapter.getSafetyStockLevel(productId);
            int reorderThreshold = inventoryAdapter.getReorderThreshold(productId);
            int leadTime = inventoryAdapter.getLeadTime(productId);

            // Calculate replenishment metrics from forecast
            BigDecimal averageForecastedDemand = calculateAverageDemand(forecastResult);
            BigDecimal maxForecastedDemand = calculateMaxDemand(forecastResult);
            int forecastDays = calculateForecastHorizon(forecastResult);

            // Calculate demand during lead time
            BigDecimal demandDuringLeadTime = averageForecastedDemand
                .multiply(BigDecimal.valueOf(leadTime))
                .setScale(0, RoundingMode.HALF_UP);

            // Calculate total demand in forecast period
            BigDecimal totalDemandInPeriod = averageForecastedDemand
                .multiply(BigDecimal.valueOf(forecastDays))
                .setScale(0, RoundingMode.HALF_UP);

            // Calculate recommended order quantity
            int recommendedQuantity = calculateOrderQuantity(
                currentStock,
                safetyStock,
                demandDuringLeadTime.intValue(),
                totalDemandInPeriod.intValue(),
                reorderThreshold
            );

            // Determine urgency level
            String urgencyLevel = determineUrgencyLevel(
                currentStock,
                safetyStock,
                reorderThreshold,
                averageForecastedDemand
            );

            // Calculate recommended order date
            LocalDate recommendedOrderDate = calculateOptimalOrderDate(
                currentStock,
                averageForecastedDemand.doubleValue(),
                leadTime,
                urgencyLevel
            );

            // Create recommendation object
            ReplenishmentRecommendation recommendation = new ReplenishmentRecommendation(
                productId,
                locationId,
                forecastResult.getStoreId()
            );

            recommendation.setCurrentStock(currentStock);
            recommendation.setSafetyStockLevel(safetyStock);
            recommendation.setReorderThreshold(reorderThreshold);
            recommendation.setRecommendedOrderQuantity(recommendedQuantity);
            recommendation.setRecommendedOrderDate(recommendedOrderDate);
            recommendation.setRecommendedDeliveryDate(recommendedOrderDate.plusDays(leadTime));
            recommendation.setForecastedAverageDemand(averageForecastedDemand);
            recommendation.setForecastedPeakDemand(maxForecastedDemand);
            recommendation.setConfidenceIntervalLower(forecastResult.getConfidenceIntervalLower() != null ?
                forecastResult.getConfidenceIntervalLower().get(0) : null);
            recommendation.setConfidenceIntervalUpper(forecastResult.getConfidenceIntervalUpper() != null ?
                forecastResult.getConfidenceIntervalUpper().get(0) : null);
            recommendation.setForecastStartDate(forecastResult.getForecastStartDate());
            recommendation.setForecastEndDate(forecastResult.getForecastEndDate());
            recommendation.setUrgencyLevel(urgencyLevel);
            recommendation.setModelUsed(forecastResult.getModelUsed());
            recommendation.setConfidence(calculateConfidenceScore(forecastResult));
            recommendation.setRationale(buildRecommendationRationale(
                currentStock,
                safetyStock,
                recommendedQuantity,
                urgencyLevel,
                forecastDays
            ));
            recommendation.setGeneratedDate(LocalDate.now());

            LOGGER.info("Generated recommendation: " + recommendation);
            return recommendation;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                "Exception while generating recommendation for product=[" + productId + "]", e);
            throw new ForecastingException(
                ErrorCode.INSUFFICIENT_HISTORY,
                "Failed to generate replenishment recommendation: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Executes a replenishment order based on a recommendation.
     *
     * @param recommendation the replenishment recommendation
     * @param supplierId the supplier identifier
     * @param orderReference the order reference ID
     * @return true if the order was successfully executed, false otherwise
     */
    public boolean executeReplenishmentOrder(
        ReplenishmentRecommendation recommendation,
        String supplierId,
        String orderReference
    ) {
        Objects.requireNonNull(recommendation, "ReplenishmentRecommendation must not be null");

        LOGGER.info("Executing replenishment order: product=[" + recommendation.getProductId() +
            "], quantity=[" + recommendation.getRecommendedOrderQuantity() +
            "], reference=[" + orderReference + "]");

        try {
            boolean success = inventoryAdapter.recordReplenishmentOrder(
                recommendation.getProductId(),
                recommendation.getLocationId(),
                recommendation.getRecommendedOrderQuantity(),
                supplierId,
                orderReference
            );

            if (success) {
                LOGGER.info("Replenishment order executed successfully: " + orderReference);
                recommendation.setApproved(true);
                return true;
            } else {
                LOGGER.warning("Replenishment order execution failed: " + orderReference);
                exceptionSource.fireMissingInputData(
                    552,
                    "InventoryIntegrationService.executeReplenishmentOrder",
                    "ReplenishmentRecording",
                    "Failed to record order in inventory system"
                );
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception while executing replenishment order", e);
            exceptionSource.fireAlgorithmicAlert(
                553,
                "InventoryIntegrationService.executeReplenishmentOrder",
                recommendation.getProductId() + "/" + recommendation.getLocationId(),
                "Exception: " + e.getMessage()
            );
            return false;
        }
    }

    /**
     * Retrieves inventory parameters for a product at a location.
     *
     * @param productId the product identifier
     * @param locationId the location identifier
     * @return InventoryParameters containing current state and policy parameters
     */
    public InventoryParameters getInventoryParameters(String productId, String locationId) {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(locationId, "locationId must not be null");

        LOGGER.fine("Retrieving inventory parameters for product=[" + productId +
            "] at location=[" + locationId + "]");

        InventoryParameters params = new InventoryParameters(productId, locationId, "");

        try {
            params.setCurrentStockLevel(inventoryAdapter.getCurrentStockLevel(productId, locationId));
            params.setAvailableQuantity(inventoryAdapter.getAvailableQuantity(productId, locationId));
            params.setReservedQuantity(inventoryAdapter.getReservedQuantity(productId, locationId));
            params.setSafetyStockLevel(inventoryAdapter.getSafetyStockLevel(productId));
            params.setReorderThreshold(inventoryAdapter.getReorderThreshold(productId));
            params.setReplenishmentLeadTimeDays(inventoryAdapter.getLeadTime(productId));
            params.setAbcCategory(inventoryAdapter.getABCCategory(productId));
            params.setAverageDailyDemand(BigDecimal.valueOf(
                inventoryAdapter.getAverageDailyConsumption(productId, locationId, 30)
            ));

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error retrieving inventory parameters", e);
        }

        return params;
    }

    // ============ Helper Methods ============

    private BigDecimal calculateAverageDemand(ForecastResult forecastResult) {
        if (forecastResult.getForecastedDemand() == null ||
            forecastResult.getForecastedDemand().isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = forecastResult.getForecastedDemand()
            .stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(
            BigDecimal.valueOf(forecastResult.getForecastedDemand().size()),
            2,
            RoundingMode.HALF_UP
        );
    }

    private BigDecimal calculateMaxDemand(ForecastResult forecastResult) {
        if (forecastResult.getForecastedDemand() == null ||
            forecastResult.getForecastedDemand().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return forecastResult.getForecastedDemand()
            .stream()
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    }

    private int calculateForecastHorizon(ForecastResult forecastResult) {
        if (forecastResult.getForecastStartDate() == null ||
            forecastResult.getForecastEndDate() == null) {
            return 30; // default
        }

        return (int) forecastResult.getForecastStartDate()
            .until(forecastResult.getForecastEndDate())
            .getDays() + 1;
    }

    private int calculateOrderQuantity(
        int currentStock,
        int safetyStock,
        int demandDuringLeadTime,
        int totalDemandInPeriod,
        int reorderThreshold
    ) {
        int targetStock = safetyStock + demandDuringLeadTime;
        int shortage = Math.max(0, targetStock - currentStock);
        int bufferForPeak = Math.max(0, (totalDemandInPeriod - shortage) / 2);

        return shortage + bufferForPeak;
    }

    private String determineUrgencyLevel(
        int currentStock,
        int safetyStock,
        int reorderThreshold,
        BigDecimal averageDemand
    ) {
        if (currentStock <= safetyStock) {
            return "CRITICAL";
        } else if (currentStock <= reorderThreshold) {
            return "HIGH";
        } else if (currentStock <= (reorderThreshold + averageDemand.intValue() * 7)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private LocalDate calculateOptimalOrderDate(
        int currentStock,
        double avgDailyDemand,
        int leadTimeDays,
        String urgencyLevel
    ) {
        if ("CRITICAL".equals(urgencyLevel)) {
            return LocalDate.now();
        }

        if (avgDailyDemand <= 0) {
            return LocalDate.now().plusDays(leadTimeDays);
        }

        int daysOfStock = (int) (currentStock / avgDailyDemand);
        int daysUntilReorder = Math.max(0, daysOfStock - leadTimeDays);

        return LocalDate.now().plusDays(daysUntilReorder);
    }

    private double calculateConfidenceScore(ForecastResult forecastResult) {
        if (forecastResult.getMape() == null) {
            return 0.7;
        }

        double mape = forecastResult.getMape().doubleValue();
        return Math.max(0.0, Math.min(1.0, 1.0 - (mape / 100.0)));
    }

    private String buildRecommendationRationale(
        int currentStock,
        int safetyStock,
        int recommendedQuantity,
        String urgencyLevel,
        int forecastDays
    ) {
        return String.format(
            "Based on %d-day forecast: current=%d, safety=%d, urgency=%s, recommend=%d units",
            forecastDays,
            currentStock,
            safetyStock,
            urgencyLevel,
            recommendedQuantity
        );
    }

    private void alertCriticalStock(ReplenishmentRecommendation recommendation) {
        LOGGER.warning("CRITICAL stock alert for product=[" + recommendation.getProductId() +
            "] at location=[" + recommendation.getLocationId() + "]");

        exceptionSource.fireAlgorithmicAlert(
            554,
            "InventoryIntegrationService",
            recommendation.getProductId() + "/" + recommendation.getLocationId(),
            "Critical stock level detected: current=" + recommendation.getCurrentStock() +
                ", safety=" + recommendation.getSafetyStockLevel()
        );
    }

    private void addToHistory(ReplenishmentRecommendation recommendation) {
        recommendationHistory.add(recommendation);

        // Maintain cache size
        if (recommendationHistory.size() > RECOMMENDATION_CACHE_SIZE) {
            recommendationHistory.remove(0);
        }
    }

    /**
     * Retrieves the history of generated recommendations.
     *
     * @return list of ReplenishmentRecommendations
     */
    public List<ReplenishmentRecommendation> getRecommendationHistory() {
        return new ArrayList<>(recommendationHistory);
    }
}
