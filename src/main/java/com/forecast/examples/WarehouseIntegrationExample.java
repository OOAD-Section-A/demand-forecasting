package com.forecast.examples;

import com.forecast.integration.warehouse.IWarehouseAdapter;
import com.forecast.integration.warehouse.WarehouseManagementAdapter;
import com.forecast.integration.warehouse.WarehouseParameters;
import com.forecast.models.ForecastResult;
import com.forecast.services.warehouse.ForecastToWarehouseIntegrationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Example demonstrating the integration between Demand Forecasting
 * and Warehouse Management System (WMS).
 *
 * This example shows:
 * 1. Initializing the warehouse adapter
 * 2. Creating forecast results with the correct API
 * 3. Processing forecasts to generate warehouse replenishment actions
 * 4. Handling different urgency levels
 * 5. Batch processing multiple forecasts
 *
 * @author Demand Forecasting Team
 * @version 1.0
 */
public class WarehouseIntegrationExample {

    private static final Logger logger = Logger.getLogger(
        WarehouseIntegrationExample.class.getName()
    );

    public static void main(String[] args) {
        setupLogging();

        System.out.println("=================================================");
        System.out.println("  Warehouse Integration Example");
        System.out.println("  Demand Forecasting + WMS");
        System.out.println(
            "=================================================\n"
        );

        try {
            // Initialize warehouse adapter (in production, pass actual WMS facade)
            IWarehouseAdapter warehouseAdapter = initializeWarehouseAdapter();

            if (warehouseAdapter == null) {
                System.out.println(
                    "[WARNING] Warehouse adapter initialization failed."
                );
                System.out.println(
                    "This example demonstrates the integration pattern."
                );
                System.out.println(
                    "In production, ensure WMS facade is properly initialized.\n"
                );
                warehouseAdapter = new WarehouseManagementAdapter(null);
            }

            // Create integration service
            ForecastToWarehouseIntegrationService integrationService =
                new ForecastToWarehouseIntegrationService(
                    warehouseAdapter,
                    false
                );

            // Run example scenarios
            runScenario1_CriticalStock(integrationService);
            runScenario2_HighUrgencyReplenishment(integrationService);
            runScenario3_MediumUrgencyReplenishment(integrationService);
            runScenario4_LowUrgencyReplenishment(integrationService);
            runScenario5_BatchProcessing(integrationService);

            // Print warehouse status
            printWarehouseStatus(warehouseAdapter);

            System.out.println(
                "\n================================================="
            );
            System.out.println("  Example Completed Successfully");
            System.out.println(
                "================================================="
            );
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Example execution failed", e);
            e.printStackTrace();
        }
    }

    /**
     * Scenario 1: Critical stock level - immediate action required
     */
    private static void runScenario1_CriticalStock(
        ForecastToWarehouseIntegrationService service
    ) {
        System.out.println("\n[SCENARIO 1] Critical Stock Level");
        System.out.println("---------------------------------");

        ForecastResult forecast = createForecastResult(
            "PROD-LAPTOP-001",
            "STORE-001",
            500.0,
            50.0,
            0.95
        );

        ForecastToWarehouseIntegrationService.WarehouseReplenishmentAction action =
            service.processForecast(forecast);

        printAction(action);
    }

    /**
     * Scenario 2: High urgency replenishment
     */
    private static void runScenario2_HighUrgencyReplenishment(
        ForecastToWarehouseIntegrationService service
    ) {
        System.out.println("\n[SCENARIO 2] High Urgency Replenishment");
        System.out.println("----------------------------------------");

        ForecastResult forecast = createForecastResult(
            "PROD-MONITOR-002",
            "STORE-001",
            300.0,
            30.0,
            0.92
        );

        ForecastToWarehouseIntegrationService.WarehouseReplenishmentAction action =
            service.processForecast(forecast);

        printAction(action);
    }

    /**
     * Scenario 3: Medium urgency replenishment
     */
    private static void runScenario3_MediumUrgencyReplenishment(
        ForecastToWarehouseIntegrationService service
    ) {
        System.out.println("\n[SCENARIO 3] Medium Urgency Replenishment");
        System.out.println("------------------------------------------");

        ForecastResult forecast = createForecastResult(
            "PROD-KEYBOARD-003",
            "STORE-001",
            200.0,
            20.0,
            0.88
        );

        ForecastToWarehouseIntegrationService.WarehouseReplenishmentAction action =
            service.processForecast(forecast);

        printAction(action);
    }

    /**
     * Scenario 4: Low urgency - normal stock levels
     */
    private static void runScenario4_LowUrgencyReplenishment(
        ForecastToWarehouseIntegrationService service
    ) {
        System.out.println("\n[SCENARIO 4] Low Urgency Replenishment");
        System.out.println("---------------------------------------");

        ForecastResult forecast = createForecastResult(
            "PROD-MOUSE-004",
            "STORE-001",
            150.0,
            15.0,
            0.90
        );

        ForecastToWarehouseIntegrationService.WarehouseReplenishmentAction action =
            service.processForecast(forecast);

        printAction(action);
    }

    /**
     * Scenario 5: Batch processing multiple forecasts
     */
    private static void runScenario5_BatchProcessing(
        ForecastToWarehouseIntegrationService service
    ) {
        System.out.println(
            "\n[SCENARIO 5] Batch Processing Multiple Forecasts"
        );
        System.out.println(
            "---------------------------------------------------"
        );

        List<ForecastResult> forecasts = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            ForecastResult forecast = createForecastResult(
                "PROD-BATCH-" + String.format("%03d", i),
                "STORE-001",
                200.0 + (i * 50),
                20.0 + i,
                0.85 + (i * 0.03)
            );
            forecasts.add(forecast);
        }

        List<
            ForecastToWarehouseIntegrationService.WarehouseReplenishmentAction
        > actions = service.processForecastBatch(forecasts);

        System.out.println("Batch Processing Results:");
        System.out.println("Total Forecasts: " + forecasts.size());
        System.out.println("Total Actions Generated: " + actions.size());

        for (int i = 0; i < actions.size(); i++) {
            System.out.println("\n  Action " + (i + 1) + ":");
            ForecastToWarehouseIntegrationService.WarehouseReplenishmentAction action =
                actions.get(i);
            System.out.println("    Forecast ID: " + action.getForecastId());
            System.out.println("    Product SKU: " + action.getProductSku());
            System.out.println("    Urgency: " + action.getUrgencyLevel());
            System.out.println(
                "    Order Quantity: " + action.getCalculatedOrderQuantity()
            );
            System.out.println("    Status: " + action.getStatus());
        }
    }

    /**
     * Creates a ForecastResult with the given parameters.
     * Demonstrates the correct API for ForecastResult initialization.
     *
     * @param productId the product identifier
     * @param storeId the store identifier
     * @param meanDemand the mean forecasted demand
     * @param standardDeviation the standard deviation
     * @param confidenceLevel the confidence level (0-1)
     * @return A properly initialized ForecastResult
     */
    private static ForecastResult createForecastResult(
        String productId,
        String storeId,
        double meanDemand,
        double standardDeviation,
        double confidenceLevel
    ) {
        ForecastResult forecast = new ForecastResult();

        // Set product and store identifiers
        forecast.setProductId(productId);
        forecast.setStoreId(storeId);

        // Set forecast dates
        LocalDate today = LocalDate.now();
        forecast.setForecastGeneratedDate(today);
        forecast.setForecastStartDate(today.plusDays(1));
        forecast.setForecastEndDate(today.plusDays(30));

        // Create forecasted demand list (simulating 30 days of forecasts)
        List<BigDecimal> forecastedDemand = new ArrayList<>();
        double dailyDemand = meanDemand / 30.0;
        for (int i = 0; i < 30; i++) {
            forecastedDemand.add(new BigDecimal(dailyDemand));
        }
        forecast.setForecastedDemand(forecastedDemand);

        // Calculate and set confidence intervals based on standard deviation
        // For a 95% confidence interval, use approximately 2 standard deviations
        List<BigDecimal> lowerBound = new ArrayList<>();
        List<BigDecimal> upperBound = new ArrayList<>();

        double zScore = getZScore(confidenceLevel); // Get appropriate z-score for confidence level
        double marginOfError = zScore * standardDeviation;

        for (int i = 0; i < 30; i++) {
            double lower = dailyDemand - marginOfError;
            double upper = dailyDemand + marginOfError;
            lowerBound.add(new BigDecimal(Math.max(lower, 0))); // Ensure non-negative
            upperBound.add(new BigDecimal(upper));
        }

        forecast.setConfidenceIntervalLower(lowerBound);
        forecast.setConfidenceIntervalUpper(upperBound);

        // Set accuracy metrics
        BigDecimal mape = new BigDecimal(5.5); // Mean Absolute Percentage Error (example: 5.5%)
        BigDecimal rmse = new BigDecimal(standardDeviation * 0.8); // RMSE (example)
        forecast.setMape(mape);
        forecast.setRmse(rmse);

        // Set additional metadata
        forecast.setLifecycleStage("PRODUCTION");
        forecast.setModelUsed("EXPONENTIAL_SMOOTHING");
        forecast.setStatus("SUCCESS");

        return forecast;
    }

    /**
     * Returns the z-score for a given confidence level.
     *
     * @param confidenceLevel the confidence level (0-1)
     * @return the corresponding z-score
     */
    private static double getZScore(double confidenceLevel) {
        // Common z-scores for standard confidence levels
        if (confidenceLevel >= 0.99) {
            return 2.576; // 99% confidence
        } else if (confidenceLevel >= 0.95) {
            return 1.96; // 95% confidence
        } else if (confidenceLevel >= 0.90) {
            return 1.645; // 90% confidence
        } else if (confidenceLevel >= 0.80) {
            return 1.282; // 80% confidence
        } else {
            return 1.0; // Default
        }
    }

    /**
     * Initializes the warehouse adapter.
     * In a real scenario, this would initialize with actual WMS facade.
     *
     * @return Initialized warehouse adapter, or null if WMS is unavailable
     */
    private static IWarehouseAdapter initializeWarehouseAdapter() {
        try {
            System.out.println("Initializing Warehouse Adapter...");

            // Try to load WMS facade from JAR
            try {
                Class<?> facadeClass = Class.forName(
                    "wms.services.WarehouseFacade"
                );
                System.out.println(
                    "WMS WarehouseFacade class found on classpath."
                );

                // In a real scenario, you would initialize the WMS here
                // For this example, we'll create adapter with null facade to demonstrate pattern
                Object facade = null; // Would be actual facade instance in production

                IWarehouseAdapter adapter = new WarehouseManagementAdapter(
                    facade
                );
                System.out.println(
                    "Warehouse Adapter initialized successfully.\n"
                );
                return adapter;
            } catch (ClassNotFoundException e) {
                System.out.println("WMS classes not available on classpath.");
                System.out.println(
                    "Make sure WMS JAR files are in lib/ directory.\n"
                );
                return new WarehouseManagementAdapter(null);
            }
        } catch (Exception e) {
            logger.log(
                Level.WARNING,
                "Error initializing warehouse adapter",
                e
            );
            return null;
        }
    }

    /**
     * Prints details of a replenishment action.
     *
     * @param action The action to print
     */
    private static void printAction(
        ForecastToWarehouseIntegrationService.WarehouseReplenishmentAction action
    ) {
        if (action == null) {
            System.out.println("ERROR: Action is null");
            return;
        }

        System.out.println("Forecast ID: " + action.getForecastId());
        System.out.println("Product SKU: " + action.getProductSku());
        System.out.println("Current Stock: " + action.getCurrentStock());
        System.out.println(
            "Forecasted Demand: " + action.getForecastedDemand()
        );
        System.out.println("Reorder Point: " + action.getReorderPoint());
        System.out.println("Safety Stock: " + action.getSafetyStock());
        System.out.println(
            "Calculated Order Quantity: " + action.getCalculatedOrderQuantity()
        );
        System.out.println(
            "Demand During Lead Time: " +
                action.getRecommendedDemandDuringLeadTime()
        );
        System.out.println("Urgency Level: " + action.getUrgencyLevel());
        System.out.println(
            "Target Delivery Date: " + action.getTargetDeliveryDate()
        );
        System.out.println(
            "Replenishment Order ID: " + action.getReplenishmentOrderId()
        );
        System.out.println(
            "Confidence Score: " +
                String.format("%.2f", action.getConfidenceScore())
        );
        System.out.println("Status: " + action.getStatus());
        System.out.println("Executed: " + action.isExecuted());
        System.out.println("Rationale: " + action.getRationale());

        if (action.getError() != null) {
            System.out.println("Error: " + action.getError());
        }
    }

    /**
     * Prints warehouse status information.
     *
     * @param adapter The warehouse adapter
     */
    private static void printWarehouseStatus(IWarehouseAdapter adapter) {
        System.out.println(
            "\n================================================="
        );
        System.out.println("  Warehouse Status");
        System.out.println("=================================================");

        System.out.println(adapter.getWarehouseStatus());

        WarehouseParameters params = adapter.getWarehouseParameters();
        System.out.println("\nWarehouse Configuration:");
        System.out.println("  Warehouse ID: " + params.getWarehouseId());
        System.out.println("  Warehouse Name: " + params.getWarehouseName());
        System.out.println("  Location: " + params.getLocation());
        System.out.println(
            "  Default Lead Time: " + params.getDefaultLeadTimeDays() + " days"
        );
        System.out.println(
            "  Safety Stock Multiplier: " + params.getSafetyStockMultiplier()
        );
        System.out.println(
            "  Minimum Order Quantity: " + params.getMinimumOrderQuantity()
        );
        System.out.println(
            "  Maximum Order Quantity: " + params.getMaximumOrderQuantity()
        );
        System.out.println(
            "  Reorder Threshold: " + params.getReorderThreshold()
        );
    }

    /**
     * Sets up logging for the example.
     */
    private static void setupLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);

        for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setLevel(Level.INFO);
                handler.setFormatter(new SimpleFormatter());
            }
        }
    }
}
