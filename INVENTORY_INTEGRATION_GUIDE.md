# Inventory Management Integration Guide

## Overview

This document describes the integration of the Inventory Management subsystem with the Demand Forecasting system. The integration enables the demand forecasting engine to automatically generate inventory replenishment recommendations based on predicted demand, creating a cohesive supply chain management solution.

## Architecture

### Integration Layers

The integration is organized into several layers:

```
┌─────────────────────────────────────────────────────────────┐
│         Forecast Controller & Output Service                │
├─────────────────────────────────────────────────────────────┤
│         Inventory Integration Service (Facade)              │
├─────────────────────────────────────────────────────────────┤
│         Inventory Adapter (Interface & Implementation)      │
├─────────────────────────────────────────────────────────────┤
│         Inventory Management Subsystem                      │
│    (InventoryService, InventoryRepository, etc.)           │
└─────────────────────────────────────────────────────────────┘
```

### Core Components

#### 1. **IInventoryAdapter** (Interface)
- **Location:** `com.forecast.integration.inventory.IInventoryAdapter`
- **Purpose:** Defines the contract for all inventory operations
- **Key Methods:**
  - `getCurrentStockLevel()` - Query current inventory
  - `recordReplenishmentOrder()` - Execute stock additions
  - `getLeadTime()`, `getSafetyStockLevel()`, `getReorderThreshold()` - Query inventory parameters
  - `isInventorySystemAvailable()` - Health check

#### 2. **InventoryManagementAdapter** (Implementation)
- **Location:** `com.forecast.integration.inventory.InventoryManagementAdapter`
- **Purpose:** Concrete implementation of IInventoryAdapter that wraps InventoryService
- **Responsibilities:**
  - Query current inventory state
  - Calculate replenishment metrics
  - Execute stock operations
  - Handle errors gracefully with logging

#### 3. **InventoryIntegrationService** (Facade)
- **Location:** `com.forecast.services.inventory.InventoryIntegrationService`
- **Purpose:** High-level orchestration of inventory-forecast integration
- **Key Methods:**
  - `processForecasterGenerateRecommendations()` - Generate recommendations for multiple locations
  - `generateRecommendationForLocation()` - Generate single recommendation
  - `executeReplenishmentOrder()` - Execute an approved recommendation
  - `getInventoryParameters()` - Query inventory state

#### 4. **ReplenishmentRecommendation** (Domain Model)
- **Location:** `com.forecast.models.inventory.ReplenishmentRecommendation`
- **Purpose:** Represents a specific replenishment action recommendation
- **Fields:**
  - Current inventory state (stock level, reserved qty)
  - Forecast data (demand, confidence intervals)
  - Recommendation details (order quantity, dates, urgency)
  - Metadata (model used, generated date)

#### 5. **InventoryParameters** (Domain Model)
- **Location:** `com.forecast.integration.inventory.InventoryParameters`
- **Purpose:** Encapsulates all inventory configuration and state for a product-location
- **Contains:**
  - Inventory levels (current, available, reserved)
  - Policy parameters (safety stock, reorder threshold, EOQ)
  - Demand characteristics
  - ABC classification
  - Forecast integration data

## Integration Flow

### Basic Flow: Forecast → Inventory Recommendation

```
1. ForecastProcessor generates ForecastResult
         ↓
2. ForecastOutputService.publishForecast(result) is called
         ↓
3. ForecastOutputService persists forecast to database
         ↓
4. ForecastOutputService.generateInventoryRecommendations(result) is called
         ↓
5. InventoryIntegrationService processes forecast for all locations
         ↓
6. For each location:
   - Query current inventory via InventoryAdapter
   - Calculate forecast metrics (avg demand, peak demand, etc.)
   - Determine urgency level based on stock/demand ratio
   - Generate ReplenishmentRecommendation
   - Alert if CRITICAL condition detected
         ↓
7. Recommendations available for manual review or auto-execution
```

### Detailed Replenishment Calculation

```
Input: 
  - Current Stock: 150 units
  - Safety Stock Level: 100 units
  - Reorder Threshold: 200 units
  - Lead Time: 7 days
  - Forecasted Avg Daily Demand: 15 units
  - Forecast Period: 30 days

Calculations:
  - Demand during lead time: 15 × 7 = 105 units
  - Total demand in forecast period: 15 × 30 = 450 units
  - Target stock at reorder: 100 + 105 = 205 units
  - Current shortage: max(0, 205 - 150) = 55 units
  - Additional stock needed: max(0, 450 - 55) = 395 units
  - Recommended order: 55 + 395 = 450 units

Output:
  - Recommended Order Quantity: 450 units
  - Urgency Level: HIGH (current < reorder threshold)
  - Recommended Order Date: 3 days from now
  - Recommended Delivery Date: 10 days from now
```

## Setup and Configuration

### 1. Initialize the Adapter

```java
import com.forecast.integration.inventory.InventoryManagementAdapter;
import com.forecast.integration.inventory.IInventoryAdapter;
import inventory_subsystem.InventoryService;

// Get or create InventoryService instance
InventoryService inventoryService = new InventoryService();

// Wrap it with the adapter
IInventoryAdapter adapter = new InventoryManagementAdapter(inventoryService);
```

### 2. Initialize the Integration Service

```java
import com.forecast.services.inventory.InventoryIntegrationService;
import com.forecast.models.exceptions.IMLAlgorithmicExceptionSource;

// Assuming you have exception source
IMLAlgorithmicExceptionSource exceptionSource = ...; // Your implementation

// Create integration service
InventoryIntegrationService integrationService = 
    new InventoryIntegrationService(adapter, exceptionSource);
```

### 3. Update ForecastOutputService

```java
import com.forecast.services.output.ForecastOutputService;
import com.forecast.integration.db.ForecastPersistenceService;

ForecastPersistenceService persistenceService = ...; // Your implementation
IMLAlgorithmicExceptionSource exceptionSource = ...; // Your implementation

// Create output service with inventory integration
ForecastOutputService outputService = new ForecastOutputService(
    exceptionSource,
    persistenceService,
    adapter,
    false // Set to true for auto-execution of replenishment orders
);

// Enable inventory integration
outputService.setInventoryIntegrationEnabled(true);
```

## Usage Examples

### Example 1: Generate Recommendations for a Single Product

```java
// Assume you have a forecast result
ForecastResult forecastResult = ...; // Generated by ForecastProcessor

// Get the integration service
InventoryIntegrationService service = ...;

// Generate recommendations for all locations
List<String> locations = Arrays.asList("WAREHOUSE_A", "WAREHOUSE_B", "STORE_01");
List<ReplenishmentRecommendation> recommendations = 
    service.processForecasterGenerateRecommendations(forecastResult, locations);

// Review recommendations
for (ReplenishmentRecommendation rec : recommendations) {
    System.out.println("Product: " + rec.getProductId());
    System.out.println("Location: " + rec.getLocationId());
    System.out.println("Current Stock: " + rec.getCurrentStock());
    System.out.println("Recommended Order: " + rec.getRecommendedOrderQuantity());
    System.out.println("Urgency: " + rec.getUrgencyLevel());
    System.out.println("Order Date: " + rec.getRecommendedOrderDate());
}
```

### Example 2: Execute a Replenishment Order

```java
// Get a recommendation
ReplenishmentRecommendation recommendation = ...;

// Execute the order
String supplierID = "SUPPLIER_001";
String orderReference = "PO_20240115_001";

boolean success = service.executeReplenishmentOrder(
    recommendation,
    supplierID,
    orderReference
);

if (success) {
    System.out.println("Order executed successfully!");
} else {
    System.out.println("Order execution failed!");
}
```

### Example 3: Query Current Inventory Parameters

```java
String productId = "PROD_001";
String locationId = "WAREHOUSE_A";

// Get current inventory state
InventoryParameters params = service.getInventoryParameters(productId, locationId);

System.out.println("Current Stock: " + params.getCurrentStockLevel());
System.out.println("Available: " + params.getAvailableQuantity());
System.out.println("Reserved: " + params.getReservedQuantity());
System.out.println("Safety Stock: " + params.getSafetyStockLevel());
System.out.println("Reorder Threshold: " + params.getReorderThreshold());
System.out.println("Lead Time: " + params.getReplenishmentLeadTimeDays() + " days");
System.out.println("ABC Category: " + params.getAbcCategory());
```

### Example 4: Batch Processing Multiple Forecasts

```java
List<ForecastResult> forecasts = ...; // Multiple forecast results
String warehouseLocation = "WAREHOUSE_A";

List<ReplenishmentRecommendation> allRecommendations = new ArrayList<>();

for (ForecastResult forecast : forecasts) {
    List<ReplenishmentRecommendation> recommendations = 
        service.processForecasterGenerateRecommendations(
            forecast,
            Arrays.asList(warehouseLocation)
        );
    allRecommendations.addAll(recommendations);
}

// Filter for critical items only
List<ReplenishmentRecommendation> criticalItems = allRecommendations.stream()
    .filter(r -> "CRITICAL".equals(r.getUrgencyLevel()))
    .collect(Collectors.toList());

System.out.println("Critical items requiring immediate attention: " + criticalItems.size());
```

## Urgency Levels

The integration service classifies replenishment recommendations into urgency levels:

| Level    | Condition | Action |
|----------|-----------|--------|
| CRITICAL | Stock ≤ Safety Stock | Immediate replenishment required |
| HIGH     | Stock ≤ Reorder Threshold | Order should be placed soon |
| MEDIUM   | Stock covers < 1 week of forecast demand | Consider ordering |
| LOW      | Adequate stock for forecast period | Routine ordering |

## Error Handling

### Exception Handling

All integration operations include error handling:

```java
try {
    ReplenishmentRecommendation rec = 
        service.generateRecommendationForLocation(forecastResult, locationId);
} catch (ForecastingException e) {
    // Log and handle forecasting-specific errors
    logger.log(Level.SEVERE, "Forecasting error: " + e.getMessage(), e);
} catch (Exception e) {
    // Handle other exceptions
    logger.log(Level.SEVERE, "Unexpected error: " + e.getMessage(), e);
}
```

### Inventory System Availability Check

```java
if (!adapter.isInventorySystemAvailable()) {
    logger.warning("Inventory system not available - cannot generate recommendations");
    // Fallback logic or alert
}
```

### Alerting Mechanism

Critical conditions trigger alerts through the exception source:

```java
exceptionSource.fireAlgorithmicAlert(
    alertCode,
    "InventoryIntegrationService",
    productId + "/" + locationId,
    "CRITICAL: Current stock below safety level"
);
```

## Customization Points

### 1. Customize Safety Stock Calculation

Modify `InventoryManagementAdapter.getSafetyStockLevel()`:

```java
@Override
public int getSafetyStockLevel(String productId) {
    // Custom logic based on product characteristics
    int baseStock = 100;
    double coefficient = getProductCoefficient(productId);
    return (int) (baseStock * coefficient);
}
```

### 2. Customize Urgency Determination

Modify `InventoryIntegrationService.determineUrgencyLevel()`:

```java
private String determineUrgencyLevel(...) {
    // Custom logic based on business rules
    if (currentStock <= safetyStock * 0.5) return "CRITICAL";
    if (currentStock <= reorderThreshold * 0.75) return "HIGH";
    // ... etc
}
```

### 3. Customize Order Quantity Calculation

Modify `InventoryIntegrationService.calculateOrderQuantity()`:

```java
private int calculateOrderQuantity(...) {
    // Custom EOQ formula or business logic
    // Can incorporate bulk discounts, storage constraints, etc.
    int economicOrderQuantity = calculateEOQ(productId);
    return Math.max(shortage + buffer, economicOrderQuantity);
}
```

### 4. Extend Location Strategy

Modify `ForecastOutputService.getDefaultLocations()`:

```java
private List<String> getDefaultLocations(String storeId) {
    // Query from configuration or database
    List<String> locations = locationService.getLocationsForStore(storeId);
    return locations;
}
```

## Logging

All integration operations include comprehensive logging:

```java
// Information level
LOGGER.info("Generated recommendation: " + recommendation);

// Fine level (verbose)
LOGGER.fine("Average daily consumption: " + avgConsumption);

// Warning level
LOGGER.warning("Inventory system not available");

// Severe level (errors)
LOGGER.log(Level.SEVERE, "Failed to generate recommendation", exception);
```

Enable logging in your configuration:

```properties
com.forecast.integration.inventory.level=FINE
com.forecast.services.inventory.level=FINE
com.forecast.services.output.level=INFO
```

## Best Practices

### 1. **Regular Health Checks**
Check inventory system availability before critical operations:

```java
if (!adapter.isInventorySystemAvailable()) {
    // Fallback or retry logic
}
```

### 2. **Validate Recommendations Before Execution**
Always review critical recommendations before auto-execution:

```java
if ("CRITICAL".equals(recommendation.getUrgencyLevel())) {
    // Manual approval required for critical items
    approvalQueue.add(recommendation);
} else if ("HIGH".equals(recommendation.getUrgencyLevel())) {
    // Auto-execute after threshold review
    executeReplenishmentOrder(recommendation, defaultSupplier, generateRef());
}
```

### 3. **Monitor Forecast Accuracy Impact on Inventory**
Track how forecast accuracy affects inventory performance:

```java
double mape = forecastResult.getMape().doubleValue();
if (mape > 25.0) {
    logger.warning("Low forecast accuracy - inventory may be suboptimal");
    // Consider conservative recommendations
}
```

### 4. **Handle Seasonal Variations**
Adjust safety stock levels for seasonal products:

```java
if (forecastResult.getLifecycleStage().equals("PEAK_SEASON")) {
    // Increase safety stock multiplier
    safetyStock *= 1.5;
}
```

### 5. **Implement Audit Trails**
Log all replenishment actions:

```java
auditLog.record(
    recommendation.getProductId(),
    recommendation.getLocationId(),
    recommendation.getRecommendedOrderQuantity(),
    "Order executed by " + executionContext.getUser(),
    System.currentTimeMillis()
);
```

### 6. **Batch Operations Efficiently**
Group recommendations for batch processing:

```java
List<ReplenishmentRecommendation> batch = 
    recommendations.stream()
    .filter(r -> r.getLocationId().equals("WAREHOUSE_A"))
    .collect(Collectors.toList());

// Process batch together for efficiency
processBatch(batch);
```

## Monitoring and Metrics

Key metrics to monitor:

1. **Recommendation Generation Rate**
   - Forecasts processed per hour
   - Successful recommendations vs failures

2. **Inventory Health**
   - Number of CRITICAL recommendations
   - Average days until stockout
   - Stockout incidents prevented

3. **Forecast Accuracy Impact**
   - Correlation between MAPE and order quantities
   - Forecast model effectiveness on inventory

4. **System Performance**
   - Adapter response time
   - Integration service latency
   - Error rate

## Troubleshooting

### Issue: No recommendations generated

**Cause:** Inventory system not available or forecast data invalid

**Solution:**
```java
// Check inventory system status
if (!adapter.isInventorySystemAvailable()) {
    logger.warning("Inventory system unavailable");
}

// Verify forecast data
if (forecastResult.getForecastedDemand() == null || 
    forecastResult.getForecastedDemand().isEmpty()) {
    logger.warning("Empty forecast demand");
}
```

### Issue: Recommendations show zero recommended quantity

**Cause:** Current stock already exceeds target with forecast demand

**Solution:**
This is normal - indicates adequate inventory. Consider reducing order quantity minimum threshold.

### Issue: Integration service throws exception

**Cause:** Missing or invalid inventory parameters

**Solution:**
```java
InventoryParameters params = service.getInventoryParameters(productId, locationId);
if (params.getSafetyStockLevel() <= 0) {
    logger.warning("Invalid safety stock configuration");
    // Set reasonable defaults
    params.setSafetyStockLevel(100);
}
```

## Migration Guide

### From Stub Integration to Full Integration

1. **Update ForecastOutputService initialization:**
```java
// Before
ForecastOutputService service = new ForecastOutputService(exceptionSource, persistenceService);

// After
ForecastOutputService service = new ForecastOutputService(
    exceptionSource,
    persistenceService,
    inventoryAdapter,
    false
);
service.setInventoryIntegrationEnabled(true);
```

2. **Update forecast processing pipeline:**
```java
ForecastResult result = forecastProcessor.processForecast(...);
// Integration now automatic through publishForecast()
forecastOutputService.publishForecast(result);
```

3. **Handle generated recommendations:**
```java
// Recommendations are generated automatically
// Access through InventoryIntegrationService
List<ReplenishmentRecommendation> recommendations = 
    integrationService.getRecommendationHistory();
```

## API Reference

See inline JavaDoc in source files:
- `IInventoryAdapter.java` - Adapter interface contract
- `InventoryManagementAdapter.java` - Implementation details
- `InventoryIntegrationService.java` - Integration orchestration
- `ReplenishmentRecommendation.java` - Domain model fields
- `InventoryParameters.java` - Parameter definitions

## Support and Maintenance

- Review logs regularly for warnings and errors
- Monitor inventory metrics against forecast accuracy
- Update safety stock parameters based on seasonal patterns
- Test integration with sample forecasts before production deployment
