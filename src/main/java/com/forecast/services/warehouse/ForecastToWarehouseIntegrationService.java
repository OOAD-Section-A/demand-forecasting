package com.forecast.services.warehouse;

import com.forecast.integration.warehouse.IWarehouseAdapter;
import com.forecast.integration.warehouse.WarehouseParameters;
import com.forecast.models.ForecastResult;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service that integrates demand forecasts with warehouse operations.
 *
 * This service:
 * - Analyzes forecast results to determine inventory needs
 * - Checks current warehouse stock levels
 * - Generates replenishment recommendations
 * - Executes replenishment orders via the warehouse adapter
 * - Monitors warehouse capacity and health
 *
 * @author Demand Forecasting Team
 * @version 1.0
 */
public class ForecastToWarehouseIntegrationService {

    private static final Logger logger = Logger.getLogger(
        ForecastToWarehouseIntegrationService.class.getName()
    );

    private IWarehouseAdapter warehouseAdapter;
    private DateTimeFormatter dateFormatter;
    private boolean autoExecuteReplenishment;

    /**
     * Constructor with warehouse adapter.
     *
     * @param warehouseAdapter The adapter for warehouse operations
     */
    public ForecastToWarehouseIntegrationService(
        IWarehouseAdapter warehouseAdapter
    ) {
        this(warehouseAdapter, false);
    }

    /**
     * Constructor with warehouse adapter and auto-execute flag.
     *
     * @param warehouseAdapter The adapter for warehouse operations
     * @param autoExecuteReplenishment Whether to automatically execute replenishment orders
     */
    public ForecastToWarehouseIntegrationService(
        IWarehouseAdapter warehouseAdapter,
        boolean autoExecuteReplenishment
    ) {
        this.warehouseAdapter = warehouseAdapter;
        this.autoExecuteReplenishment = autoExecuteReplenishment;
        this.dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        logger.info(
            "ForecastToWarehouseIntegrationService initialized. Auto-execute: " +
                autoExecuteReplenishment
        );
    }

    /**
     * Processes a single forecast result and generates warehouse replenishment action.
     *
     * @param forecast The forecast result to process
     * @return A WarehouseReplenishmentAction containing the recommendation and status
     */
    public WarehouseReplenishmentAction processForecast(
        ForecastResult forecast
    ) {
        if (forecast == null) {
            logger.warning("Null forecast received");
            return null;
        }

        if (!warehouseAdapter.isWarehouseOperational()) {
            logger.warning(
                "Warehouse is not operational, cannot process forecast"
            );
            WarehouseReplenishmentAction action =
                new WarehouseReplenishmentAction();
            action.setStatus("WAREHOUSE_UNAVAILABLE");
            return action;
        }

        try {
            WarehouseReplenishmentAction action =
                new WarehouseReplenishmentAction();
            action.setForecastId(
                forecast.getProductId() + "-" + System.currentTimeMillis()
            );
            action.setProductSku(forecast.getProductId());

            // Get current warehouse parameters and stock level
            WarehouseParameters params =
                warehouseAdapter.getWarehouseParameters();
            int currentStock = warehouseAdapter.getStockLevel(
                forecast.getProductId()
            );

            if (currentStock < 0) {
                currentStock = 0;
            }

            // Extract forecast metrics from list
            double forecastedDemand = 0;
            if (
                forecast.getForecastedDemand() != null &&
                !forecast.getForecastedDemand().isEmpty()
            ) {
                // Sum all forecasted demand values
                for (java.math.BigDecimal demand : forecast.getForecastedDemand()) {
                    forecastedDemand += demand.doubleValue();
                }
            }

            // Calculate standard deviation from confidence intervals if available
            double demandStdDev = 0;
            if (
                forecast.getConfidenceIntervalUpper() != null &&
                forecast.getConfidenceIntervalLower() != null &&
                !forecast.getConfidenceIntervalUpper().isEmpty()
            ) {
                // Estimate std dev from 95% confidence interval (approximately 2 std devs)
                java.math.BigDecimal upper = forecast
                    .getConfidenceIntervalUpper()
                    .get(0);
                java.math.BigDecimal lower = forecast
                    .getConfidenceIntervalLower()
                    .get(0);
                demandStdDev =
                    (upper.doubleValue() - lower.doubleValue()) / 4.0;
            }

            if (demandStdDev <= 0) {
                demandStdDev = Math.max(forecastedDemand * 0.2, 1.0);
            }

            // Calculate replenishment quantity
            ReplenishmentCalculation calculation =
                calculateReplenishmentQuantity(
                    currentStock,
                    forecastedDemand,
                    demandStdDev,
                    params
                );

            action.setCurrentStock(currentStock);
            action.setForecastedDemand((int) forecastedDemand);
            action.setCalculatedOrderQuantity(calculation.getOrderQuantity());
            action.setRecommendedDemandDuringLeadTime(
                calculation.getDemandDuringLeadTime()
            );
            action.setSafetyStock(calculation.getSafetyStock());
            action.setReorderPoint(calculation.getReorderPoint());

            // Determine urgency
            String urgency = determineUrgency(
                currentStock,
                calculation.getReorderPoint(),
                params,
                forecastedDemand
            );
            action.setUrgencyLevel(urgency);

            // Generate order ID
            String orderId = generateReplenishmentOrderId(
                forecast.getProductId()
            );
            action.setReplenishmentOrderId(orderId);

            // Set target delivery date
            LocalDate targetDate = LocalDate.now().plusDays(
                params.getDefaultLeadTimeDays()
            );
            action.setTargetDeliveryDate(targetDate.format(dateFormatter));

            // Add rationale
            action.setRationale(
                buildRationale(currentStock, calculation, params, urgency)
            );

            // Set confidence (default to 0.85 if not available)
            action.setConfidenceScore(0.85);

            // Execute if needed
            if (
                shouldExecuteReplenishment(
                    urgency,
                    currentStock,
                    calculation.getReorderPoint()
                )
            ) {
                boolean executed = executeReplenishment(action, forecast);
                action.setExecuted(executed);
                action.setStatus(executed ? "EXECUTED" : "EXECUTION_FAILED");
            } else {
                action.setStatus("PENDING_REVIEW");
                action.setExecuted(false);
            }

            logger.info(
                "Forecast processed for SKU " +
                    forecast.getProductId() +
                    ": Action=" +
                    action.getStatus() +
                    ", Urgency=" +
                    urgency
            );
            return action;
        } catch (Exception e) {
            logger.log(
                Level.SEVERE,
                "Error processing forecast: " + forecast.getProductId(),
                e
            );
            WarehouseReplenishmentAction errorAction =
                new WarehouseReplenishmentAction();
            errorAction.setStatus("ERROR");
            errorAction.setError(e.getMessage());
            return errorAction;
        }
    }

    /**
     * Processes multiple forecast results in batch.
     *
     * @param forecasts List of forecast results to process
     * @return List of warehouse replenishment actions
     */
    public List<WarehouseReplenishmentAction> processForecastBatch(
        List<ForecastResult> forecasts
    ) {
        List<WarehouseReplenishmentAction> actions = new ArrayList<>();

        if (forecasts == null || forecasts.isEmpty()) {
            logger.warning("Empty forecast batch received");
            return actions;
        }

        for (ForecastResult forecast : forecasts) {
            try {
                WarehouseReplenishmentAction action = processForecast(forecast);
                if (action != null) {
                    actions.add(action);
                }
            } catch (Exception e) {
                logger.log(
                    Level.WARNING,
                    "Error processing forecast in batch: " +
                        forecast.getProductId(),
                    e
                );
            }
        }

        logger.info(
            "Processed batch of " +
                forecasts.size() +
                " forecasts, " +
                actions.size() +
                " actions generated"
        );
        return actions;
    }

    /**
     * Calculates the replenishment quantity based on forecasted demand and warehouse parameters.
     *
     * @param currentStock Current stock level
     * @param forecastedDemand Forecasted demand
     * @param demandStdDev Standard deviation of demand
     * @param params Warehouse parameters
     * @return Replenishment calculation details
     */
    private ReplenishmentCalculation calculateReplenishmentQuantity(
        int currentStock,
        double forecastedDemand,
        double demandStdDev,
        WarehouseParameters params
    ) {
        ReplenishmentCalculation calc = new ReplenishmentCalculation();

        // Calculate demand during lead time
        int leadTimeDays = params.getDefaultLeadTimeDays();
        double dailyDemand = forecastedDemand / 30.0; // Assuming 30-day forecast period
        double demandDuringLeadTime = dailyDemand * leadTimeDays;
        calc.setDemandDuringLeadTime((int) Math.ceil(demandDuringLeadTime));

        // Calculate safety stock (using z-score for service level)
        // Z=1.65 for ~95% service level
        double safetyStock =
            demandStdDev *
            1.65 *
            Math.sqrt(leadTimeDays) *
            params.getSafetyStockMultiplier();
        calc.setSafetyStock((int) Math.ceil(safetyStock));

        // Calculate reorder point
        int reorderPoint =
            calc.getDemandDuringLeadTime() + calc.getSafetyStock();
        calc.setReorderPoint(reorderPoint);

        // Calculate order quantity (Economic Order Quantity approach)
        int minOrderQty = params.getMinimumOrderQuantity();
        int maxOrderQty = params.getMaximumOrderQuantity();

        // Base order = demand during lead time + safety stock
        int baseOrder = reorderPoint;

        // Round to minimum order quantity multiples
        int orderQuantity = Math.max(
            minOrderQty,
            ((baseOrder + minOrderQty - 1) / minOrderQty) * minOrderQty
        );

        // Cap at maximum
        orderQuantity = Math.min(orderQuantity, maxOrderQty);

        // Only order if below reorder point
        if (currentStock <= reorderPoint) {
            // Calculate how much we need to reach a good target level
            int targetStock =
                reorderPoint + (calc.getDemandDuringLeadTime() * 2);
            orderQuantity = Math.max(minOrderQty, targetStock - currentStock);
            orderQuantity = Math.min(orderQuantity, maxOrderQty);
        } else {
            orderQuantity = 0; // No replenishment needed
        }

        calc.setOrderQuantity(orderQuantity);
        return calc;
    }

    /**
     * Determines the urgency level of replenishment based on stock levels and demand.
     *
     * @param currentStock Current stock level
     * @param reorderPoint Reorder point threshold
     * @param params Warehouse parameters
     * @param forecastedDemand Forecasted demand
     * @return Urgency level (CRITICAL, HIGH, MEDIUM, LOW, NONE)
     */
    private String determineUrgency(
        int currentStock,
        int reorderPoint,
        WarehouseParameters params,
        double forecastedDemand
    ) {
        // Critical: Less than 1 day of demand
        if (currentStock < forecastedDemand / 30) {
            return "CRITICAL";
        }

        // High: Between 1-3 days of demand or below reorder point
        if (currentStock < reorderPoint) {
            return "HIGH";
        }

        // Medium: Close to reorder point
        if (
            currentStock <
            reorderPoint +
            (params.getDefaultLeadTimeDays() * (forecastedDemand / 30))
        ) {
            return "MEDIUM";
        }

        // Low: Adequate stock
        if (currentStock < params.getReorderThreshold()) {
            return "LOW";
        }

        // None: Healthy stock levels
        return "NONE";
    }

    /**
     * Determines if replenishment should be executed automatically.
     *
     * @param urgency Urgency level
     * @param currentStock Current stock
     * @param reorderPoint Reorder point
     * @return true if replenishment should be executed
     */
    private boolean shouldExecuteReplenishment(
        String urgency,
        int currentStock,
        int reorderPoint
    ) {
        if (!autoExecuteReplenishment) {
            return false;
        }

        // Auto-execute CRITICAL and HIGH urgency orders
        return (
            "CRITICAL".equals(urgency) ||
            ("HIGH".equals(urgency) && currentStock <= reorderPoint)
        );
    }

    /**
     * Executes replenishment order via the warehouse adapter.
     *
     * @param action The replenishment action to execute
     * @param forecast The original forecast
     * @return true if execution was successful
     */
    private boolean executeReplenishment(
        WarehouseReplenishmentAction action,
        ForecastResult forecast
    ) {
        try {
            logger.info(
                "Executing replenishment order: " +
                    action.getReplenishmentOrderId() +
                    " for SKU: " +
                    action.getProductSku()
            );

            boolean recorded = warehouseAdapter.recordReplenishmentOrder(
                action.getReplenishmentOrderId(),
                action.getProductSku(),
                action.getCalculatedOrderQuantity(),
                "DEFAULT_VENDOR",
                action.getTargetDeliveryDate()
            );

            if (recorded) {
                logger.info(
                    "Replenishment order recorded successfully: " +
                        action.getReplenishmentOrderId()
                );
            } else {
                logger.warning(
                    "Failed to record replenishment order: " +
                        action.getReplenishmentOrderId()
                );
            }

            return recorded;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error executing replenishment", e);
            return false;
        }
    }

    /**
     * Generates a unique replenishment order ID.
     *
     * @param sku Product SKU
     * @return Replenishment order ID
     */
    private String generateReplenishmentOrderId(String sku) {
        long timestamp = System.currentTimeMillis();
        return "RO-" + sku + "-" + timestamp;
    }

    /**
     * Builds a detailed rationale for the replenishment recommendation.
     *
     * @param currentStock Current stock level
     * @param calculation Replenishment calculation
     * @param params Warehouse parameters
     * @param urgency Urgency level
     * @return Detailed rationale string
     */
    private String buildRationale(
        int currentStock,
        ReplenishmentCalculation calculation,
        WarehouseParameters params,
        String urgency
    ) {
        StringBuilder rationale = new StringBuilder();
        rationale.append("Replenishment Analysis: ");
        rationale.append("Current Stock=").append(currentStock).append(", ");
        rationale
            .append("Reorder Point=")
            .append(calculation.getReorderPoint())
            .append(", ");
        rationale
            .append("Safety Stock=")
            .append(calculation.getSafetyStock())
            .append(", ");
        rationale
            .append("Lead Time=")
            .append(params.getDefaultLeadTimeDays())
            .append(" days, ");
        rationale
            .append("Recommended Order=")
            .append(calculation.getOrderQuantity())
            .append(", ");
        rationale.append("Urgency=").append(urgency);
        return rationale.toString();
    }

    /**
     * Gets the warehouse status.
     *
     * @return Warehouse status information
     */
    public String getWarehouseStatus() {
        if (warehouseAdapter == null) {
            return "Warehouse adapter not initialized";
        }
        return warehouseAdapter.getWarehouseStatus();
    }

    /**
     * Checks if warehouse is operational.
     *
     * @return true if warehouse is operational
     */
    public boolean isWarehouseOperational() {
        return (
            warehouseAdapter != null &&
            warehouseAdapter.isWarehouseOperational()
        );
    }

    /**
     * Sets whether to automatically execute replenishment orders.
     *
     * @param autoExecute true to auto-execute, false for manual review
     */
    public void setAutoExecuteReplenishment(boolean autoExecute) {
        this.autoExecuteReplenishment = autoExecute;
        logger.info("Auto-execute replenishment set to: " + autoExecute);
    }

    /**
     * Inner class representing a replenishment calculation.
     */
    public static class ReplenishmentCalculation {

        private int demandDuringLeadTime;
        private int safetyStock;
        private int reorderPoint;
        private int orderQuantity;

        public int getDemandDuringLeadTime() {
            return demandDuringLeadTime;
        }

        public void setDemandDuringLeadTime(int demandDuringLeadTime) {
            this.demandDuringLeadTime = demandDuringLeadTime;
        }

        public int getSafetyStock() {
            return safetyStock;
        }

        public void setSafetyStock(int safetyStock) {
            this.safetyStock = safetyStock;
        }

        public int getReorderPoint() {
            return reorderPoint;
        }

        public void setReorderPoint(int reorderPoint) {
            this.reorderPoint = reorderPoint;
        }

        public int getOrderQuantity() {
            return orderQuantity;
        }

        public void setOrderQuantity(int orderQuantity) {
            this.orderQuantity = orderQuantity;
        }
    }

    /**
     * Inner class representing a warehouse replenishment action.
     */
    public static class WarehouseReplenishmentAction {

        private String forecastId;
        private String productSku;
        private String replenishmentOrderId;
        private int currentStock;
        private int forecastedDemand;
        private int calculatedOrderQuantity;
        private int recommendedDemandDuringLeadTime;
        private int safetyStock;
        private int reorderPoint;
        private String urgencyLevel;
        private String targetDeliveryDate;
        private String rationale;
        private double confidenceScore;
        private String status;
        private boolean executed;
        private String error;

        // Getters and setters
        public String getForecastId() {
            return forecastId;
        }

        public void setForecastId(String forecastId) {
            this.forecastId = forecastId;
        }

        public String getProductSku() {
            return productSku;
        }

        public void setProductSku(String productSku) {
            this.productSku = productSku;
        }

        public String getReplenishmentOrderId() {
            return replenishmentOrderId;
        }

        public void setReplenishmentOrderId(String replenishmentOrderId) {
            this.replenishmentOrderId = replenishmentOrderId;
        }

        public int getCurrentStock() {
            return currentStock;
        }

        public void setCurrentStock(int currentStock) {
            this.currentStock = currentStock;
        }

        public int getForecastedDemand() {
            return forecastedDemand;
        }

        public void setForecastedDemand(int forecastedDemand) {
            this.forecastedDemand = forecastedDemand;
        }

        public int getCalculatedOrderQuantity() {
            return calculatedOrderQuantity;
        }

        public void setCalculatedOrderQuantity(int calculatedOrderQuantity) {
            this.calculatedOrderQuantity = calculatedOrderQuantity;
        }

        public int getRecommendedDemandDuringLeadTime() {
            return recommendedDemandDuringLeadTime;
        }

        public void setRecommendedDemandDuringLeadTime(
            int recommendedDemandDuringLeadTime
        ) {
            this.recommendedDemandDuringLeadTime =
                recommendedDemandDuringLeadTime;
        }

        public int getSafetyStock() {
            return safetyStock;
        }

        public void setSafetyStock(int safetyStock) {
            this.safetyStock = safetyStock;
        }

        public int getReorderPoint() {
            return reorderPoint;
        }

        public void setReorderPoint(int reorderPoint) {
            this.reorderPoint = reorderPoint;
        }

        public String getUrgencyLevel() {
            return urgencyLevel;
        }

        public void setUrgencyLevel(String urgencyLevel) {
            this.urgencyLevel = urgencyLevel;
        }

        public String getTargetDeliveryDate() {
            return targetDeliveryDate;
        }

        public void setTargetDeliveryDate(String targetDeliveryDate) {
            this.targetDeliveryDate = targetDeliveryDate;
        }

        public String getRationale() {
            return rationale;
        }

        public void setRationale(String rationale) {
            this.rationale = rationale;
        }

        public double getConfidenceScore() {
            return confidenceScore;
        }

        public void setConfidenceScore(double confidenceScore) {
            this.confidenceScore = confidenceScore;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public boolean isExecuted() {
            return executed;
        }

        public void setExecuted(boolean executed) {
            this.executed = executed;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        @Override
        public String toString() {
            return (
                "WarehouseReplenishmentAction{" +
                "forecastId='" +
                forecastId +
                '\'' +
                ", productSku='" +
                productSku +
                '\'' +
                ", replenishmentOrderId='" +
                replenishmentOrderId +
                '\'' +
                ", currentStock=" +
                currentStock +
                ", forecastedDemand=" +
                forecastedDemand +
                ", calculatedOrderQuantity=" +
                calculatedOrderQuantity +
                ", urgencyLevel='" +
                urgencyLevel +
                '\'' +
                ", targetDeliveryDate='" +
                targetDeliveryDate +
                '\'' +
                ", status='" +
                status +
                '\'' +
                ", executed=" +
                executed +
                '}'
            );
        }
    }
}
