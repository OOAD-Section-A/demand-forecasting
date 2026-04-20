package com.forecast.examples;

import com.forecast.controllers.ForecastController;
import com.forecast.integration.inventory.InventoryManagementAdapter;
import com.forecast.integration.inventory.IInventoryAdapter;
import com.forecast.integration.db.DemandForecastingDbAdapter;
import com.forecast.integration.db.ForecastPersistenceService;
import com.forecast.models.FeatureTimeSeries;
import com.forecast.models.ForecastResult;
import com.forecast.models.LifecycleContent;
import com.forecast.models.exceptions.IMLAlgorithmicExceptionSource;
import com.forecast.models.exceptions.MLAlgorithmicExceptionSource;
import com.forecast.models.inventory.ReplenishmentRecommendation;
import com.forecast.services.engine.ForecastProcessor;
import com.forecast.services.engine.lifecycle.LifeCycleManager;
import com.forecast.services.inventory.InventoryIntegrationService;
import com.forecast.services.output.ForecastOutputService;
import inventory_subsystem.InventoryService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Example implementation demonstrating full integration of demand forecasting
 * with inventory management subsystem.
 *
 * This example shows:
 * 1. Initializing all components
 * 2. Generating forecasts
 * 3. Publishing forecasts with inventory integration
 * 4. Generating replenishment recommendations
 * 5. Executing replenishment orders
 *
 * @author Integration Example
 * @version 1.0
 */
public class InventoryIntegrationExample {

    private static final Logger LOGGER = Logger.getLogger(
        InventoryIntegrationExample.class.getName()
    );

    private final ForecastController forecastController;
    private final InventoryIntegrationService inventoryIntegration;
    private final ForecastOutputService forecastOutputService;

    /**
     * Initializes the complete integrated system.
     */
    public InventoryIntegrationExample() {
        // ============ Step 1: Initialize Exception Handling ============
        LOGGER.info("Step 1: Initializing exception handling...");
        IMLAlgorithmicExceptionSource exceptionSource = new MLAlgorithmicExceptionSource();

        // ============ Step 2: Initialize Database/Persistence ============
        LOGGER.info("Step 2: Initializing persistence services...");
        DemandForecastingDbAdapter dbAdapter = new DemandForecastingDbAdapter();
        ForecastPersistenceService persistenceService = new ForecastPersistenceService(dbAdapter);

        // ============ Step 3: Initialize Inventory Subsystem & Adapter ============
        LOGGER.info("Step 3: Initializing inventory subsystem...");
        InventoryService inventoryService = new InventoryService();
        IInventoryAdapter inventoryAdapter = new InventoryManagementAdapter(inventoryService);

        // ============ Step 4: Initialize Inventory Integration Service ============
        LOGGER.info("Step 4: Initializing inventory integration service...");
        this.inventoryIntegration = new InventoryIntegrationService(
            inventoryAdapter,
            exceptionSource
        );

        // ============ Step 5: Initialize Forecast Services ============
        LOGGER.info("Step 5: Initializing forecast services...");
        LifeCycleManager lifeCycleManager = new LifeCycleManager();
        this.forecastOutputService = new ForecastOutputService(
            exceptionSource,
            persistenceService,
            inventoryAdapter,
            false // Set to true to auto-execute critical replenishments
        );
        forecastOutputService.setInventoryIntegrationEnabled(true);

        ForecastProcessor forecastProcessor = new ForecastProcessor(
            lifeCycleManager,
            forecastOutputService,
            exceptionSource
        );

        // ============ Step 6: Initialize Controller ============
        LOGGER.info("Step 6: Initializing forecast controller...");
        this.forecastController = new ForecastController(forecastProcessor);

        LOGGER.info("System initialization complete!");
    }

    /**
     * Example 1: Generate forecast and automatically create inventory recommendations
     */
    public void example1_BasicForecastingWithInventoryIntegration() {
        LOGGER.info("\n========== EXAMPLE 1: Basic Forecasting with Inventory Integration ==========");

        // Sample data
        String productId = "PROD_SKU_001";
        String storeId = "STORE_NYC_001";

        // Create feature time series from historical data
        FeatureTimeSeries features = new FeatureTimeSeries();
        features.setProductId(productId);
        features.setStoreId(storeId);
        features.setDemandValues(Arrays.asList(
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(110),
            BigDecimal.valueOf(105),
            BigDecimal.valueOf(120),
            BigDecimal.valueOf(115),
            BigDecimal.valueOf(125),
            BigDecimal.valueOf(130)
        ));
        features.setEndDate(LocalDate.now().minusMonths(1));

        // Generate forecast
        LOGGER.info("Generating forecast for " + productId + " at " + storeId);
        ForecastResult forecast = forecastController.generateForecast(
            productId,
            storeId,
            features,
            null // Let processor determine lifecycle
        );

        if (forecast == null) {
            LOGGER.warning("Forecast generation failed");
            return;
        }

        LOGGER.info("Forecast generated successfully:");
        LOGGER.info("  - Status: " + forecast.getStatus());
        LOGGER.info("  - Model: " + forecast.getModelUsed());
        LOGGER.info("  - Forecast Period: " + forecast.getForecastStartDate() +
            " to " + forecast.getForecastEndDate());
        LOGGER.info("  - MAPE: " + forecast.getMape());

        // Note: Inventory integration happens automatically through publishForecast()
        LOGGER.info("Forecast published - inventory recommendations generated automatically");
    }

    /**
     * Example 2: Manually process forecast and review recommendations
     */
    public void example2_ManualRecommendationGeneration() {
        LOGGER.info("\n========== EXAMPLE 2: Manual Recommendation Generation ==========");

        // Sample forecast
        ForecastResult forecast = createSampleForecast(
            "PROD_SKU_002",
            "STORE_LA_001",
            Arrays.asList(50, 55, 60, 65, 70, 75, 80) // 7 data points
        );

        String locationId = "WAREHOUSE_WEST";

        // Generate recommendation manually
        LOGGER.info("Generating manual recommendation for " + forecast.getProductId());
        ReplenishmentRecommendation recommendation =
            inventoryIntegration.generateRecommendationForLocation(forecast, locationId);

        if (recommendation == null) {
            LOGGER.warning("Recommendation generation failed");
            return;
        }

        // Display recommendation details
        displayRecommendation(recommendation);

        // Approve and execute if urgency is high
        if ("HIGH".equals(recommendation.getUrgencyLevel()) ||
            "CRITICAL".equals(recommendation.getUrgencyLevel())) {

            LOGGER.info("High urgency detected - executing replenishment order");

            boolean success = inventoryIntegration.executeReplenishmentOrder(
                recommendation,
                "SUPPLIER_WHOLESALE_INC",
                generatePurchaseOrderReference()
            );

            if (success) {
                LOGGER.info("Replenishment order executed successfully!");
            } else {
                LOGGER.warning("Failed to execute replenishment order");
            }
        }
    }

    /**
     * Example 3: Batch process multiple products
     */
    public void example3_BatchProcessing() {
        LOGGER.info("\n========== EXAMPLE 3: Batch Processing Multiple Products ==========");

        String warehouseLocation = "WAREHOUSE_EAST";
        List<ForecastResult> forecasts = new ArrayList<>();

        // Generate forecasts for multiple products
        String[] products = {"SKU_101", "SKU_102", "SKU_103", "SKU_104", "SKU_105"};
        for (String sku : products) {
            ForecastResult forecast = createSampleForecast(
                sku,
                "STORE_CHI_001",
                Arrays.asList(20, 22, 25, 23, 26, 28, 30)
            );
            if (forecast != null) {
                forecasts.add(forecast);
            }
        }

        LOGGER.info("Processing " + forecasts.size() + " forecasts for batch generation");

        // Generate recommendations for all products
        List<ReplenishmentRecommendation> recommendations =
            inventoryIntegration.processBatchForecasts(forecasts, warehouseLocation);

        LOGGER.info("Generated " + recommendations.size() + " recommendations");

        // Analyze recommendations
        long criticalCount = recommendations.stream()
            .filter(r -> "CRITICAL".equals(r.getUrgencyLevel()))
            .count();
        long highCount = recommendations.stream()
            .filter(r -> "HIGH".equals(r.getUrgencyLevel()))
            .count();
        long mediumCount = recommendations.stream()
            .filter(r -> "MEDIUM".equals(r.getUrgencyLevel()))
            .count();
        long lowCount = recommendations.stream()
            .filter(r -> "LOW".equals(r.getUrgencyLevel()))
            .count();

        LOGGER.info("Recommendation Summary:");
        LOGGER.info("  - CRITICAL: " + criticalCount);
        LOGGER.info("  - HIGH: " + highCount);
        LOGGER.info("  - MEDIUM: " + mediumCount);
        LOGGER.info("  - LOW: " + lowCount);

        // Execute critical recommendations
        recommendations.stream()
            .filter(r -> "CRITICAL".equals(r.getUrgencyLevel()))
            .forEach(rec -> {
                LOGGER.info("Executing critical order for " + rec.getProductId());
                inventoryIntegration.executeReplenishmentOrder(
                    rec,
                    "SUPPLIER_EMERGENCY",
                    generatePurchaseOrderReference()
                );
            });
    }

    /**
     * Example 4: Query inventory parameters
     */
    public void example4_QueryInventoryParameters() {
        LOGGER.info("\n========== EXAMPLE 4: Query Inventory Parameters ==========");

        String productId = "PROD_SKU_001";
        String warehouseLocation = "WAREHOUSE_MAIN";

        // Get current inventory state
        var params = inventoryIntegration.getInventoryParameters(productId, warehouseLocation);

        LOGGER.info("Inventory Parameters for " + productId + " at " + warehouseLocation + ":");
        LOGGER.info("  - Current Stock: " + params.getCurrentStockLevel() + " units");
        LOGGER.info("  - Available: " + params.getAvailableQuantity() + " units");
        LOGGER.info("  - Reserved: " + params.getReservedQuantity() + " units");
        LOGGER.info("  - Safety Stock: " + params.getSafetyStockLevel() + " units");
        LOGGER.info("  - Reorder Threshold: " + params.getReorderThreshold() + " units");
        LOGGER.info("  - Lead Time: " + params.getReplenishmentLeadTimeDays() + " days");
        LOGGER.info("  - ABC Category: " + params.getAbcCategory());
        LOGGER.info("  - Avg Daily Demand: " + params.getAverageDailyDemand());
    }

    /**
     * Example 5: Handling errors and edge cases
     */
    public void example5_ErrorHandlingAndEdgeCases() {
        LOGGER.info("\n========== EXAMPLE 5: Error Handling and Edge Cases ==========");

        // Case 1: Forecast with missing demand values
        LOGGER.info("Test Case 1: Empty forecast demand");
        try {
            ForecastResult emptyForecast = new ForecastResult();
            emptyForecast.setProductId("PROD_EMPTY");
            emptyForecast.setStoreId("STORE_TEST");
            emptyForecast.setForecastedDemand(new ArrayList<>()); // Empty list

            forecastOutputService.publishForecast(emptyForecast);
            LOGGER.warning("Expected exception not thrown!");
        } catch (Exception e) {
            LOGGER.info("Caught expected exception: " + e.getClass().getSimpleName());
        }

        // Case 2: Non-existent product
        LOGGER.info("\nTest Case 2: Non-existent product");
        try {
            ReplenishmentRecommendation rec =
                inventoryIntegration.generateRecommendationForLocation(
                    createSampleForecast("NONEXISTENT_SKU", "STORE_TEST",
                        Arrays.asList(10, 20, 30)),
                    "WAREHOUSE_TEST"
                );

            if (rec == null) {
                LOGGER.info("No recommendation generated (product not found)");
            }
        } catch (Exception e) {
            LOGGER.info("Exception: " + e.getMessage());
        }

        // Case 3: Very low demand
        LOGGER.info("\nTest Case 3: Very low demand");
        ForecastResult lowDemand = createSampleForecast("SLOW_MOVING_SKU", "STORE_TEST",
            Arrays.asList(1, 1, 2, 1, 1, 2, 1)
        );
        ReplenishmentRecommendation rec =
            inventoryIntegration.generateRecommendationForLocation(lowDemand, "WAREHOUSE_TEST");

        if (rec != null) {
            LOGGER.info("Recommendation for slow-moving item:");
            LOGGER.info("  - Urgency: " + rec.getUrgencyLevel());
            LOGGER.info("  - Recommended Qty: " + rec.getRecommendedOrderQuantity());
        }
    }

    /**
     * Helper method to create sample forecast for testing
     */
    private ForecastResult createSampleForecast(
        String productId,
        String storeId,
        List<Integer> demandValues
    ) {
        ForecastResult result = new ForecastResult();
        result.setProductId(productId);
        result.setStoreId(storeId);
        result.setForecastGeneratedDate(LocalDate.now());
        result.setForecastStartDate(LocalDate.now().plusMonths(1));
        result.setForecastEndDate(LocalDate.now().plusMonths(2));

        // Convert to BigDecimal
        List<BigDecimal> bigDecimalDemand = new ArrayList<>();
        for (Integer demand : demandValues) {
            bigDecimalDemand.add(BigDecimal.valueOf(demand));
        }
        result.setForecastedDemand(bigDecimalDemand);

        // Set metadata
        result.setMape(BigDecimal.valueOf(8.5));
        result.setRmse(BigDecimal.valueOf(5.2));
        result.setModelUsed("ARIMA");
        result.setStatus("SUCCESS");
        result.setLifecycleStage("GROWTH");

        return result;
    }

    /**
     * Helper method to display recommendation details
     */
    private void displayRecommendation(ReplenishmentRecommendation rec) {
        LOGGER.info("Replenishment Recommendation Details:");
        LOGGER.info("  Product ID: " + rec.getProductId());
        LOGGER.info("  Location: " + rec.getLocationId());
        LOGGER.info("  Current Stock: " + rec.getCurrentStock() + " units");
        LOGGER.info("  Safety Stock: " + rec.getSafetyStockLevel() + " units");
        LOGGER.info("  Recommended Order Quantity: " + rec.getRecommendedOrderQuantity() + " units");
        LOGGER.info("  Urgency Level: " + rec.getUrgencyLevel());
        LOGGER.info("  Recommended Order Date: " + rec.getRecommendedOrderDate());
        LOGGER.info("  Expected Delivery Date: " + rec.getRecommendedDeliveryDate());
        LOGGER.info("  Forecasted Avg Demand: " + rec.getForecastedAverageDemand());
        LOGGER.info("  Confidence: " + (rec.getConfidence() * 100) + "%");
        LOGGER.info("  Rationale: " + rec.getRationale());
    }

    /**
     * Helper method to generate PO reference
     */
    private String generatePurchaseOrderReference() {
        return "PO_" + System.currentTimeMillis();
    }

    /**
     * Main method to run all examples
     */
    public static void main(String[] args) {
        try {
            LOGGER.info("========== INVENTORY INTEGRATION EXAMPLES ==========\n");

            InventoryIntegrationExample example = new InventoryIntegrationExample();

            // Run examples
            example.example1_BasicForecastingWithInventoryIntegration();
            example.example2_ManualRecommendationGeneration();
            example.example3_BatchProcessing();
            example.example4_QueryInventoryParameters();
            example.example5_ErrorHandlingAndEdgeCases();

            LOGGER.info("\n========== EXAMPLES COMPLETE ==========");

        } catch (Exception e) {
            LOGGER.severe("Application error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
