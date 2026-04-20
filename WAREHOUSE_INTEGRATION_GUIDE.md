# Warehouse Management System (WMS) Integration Guide

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Components](#components)
4. [Installation & Setup](#installation--setup)
5. [Usage Guide](#usage-guide)
6. [Configuration](#configuration)
7. [API Reference](#api-reference)
8. [Examples](#examples)
9. [Troubleshooting](#troubleshooting)
10. [Best Practices](#best-practices)

---

## Overview

The Warehouse Management System (WMS) integration provides seamless connectivity between the Demand Forecasting system and the Warehouse Management subsystem (SCM-Subsystem-2-WMS). This integration enables forecast-driven inventory replenishment, optimized stock management, and automated order generation based on predicted demand.

### Key Features

- **Forecast-to-Action**: Converts demand forecasts directly into warehouse replenishment recommendations
- **Real-time Stock Visibility**: Query current warehouse stock levels for any SKU
- **Smart Replenishment**: Calculates optimal order quantities based on lead times, safety stock, and demand variability
- **Urgency Leveling**: Prioritizes replenishment orders as CRITICAL, HIGH, MEDIUM, LOW, or NONE
- **Batch Processing**: Handle multiple forecasts simultaneously
- **Flexible Execution**: Support for manual review or automatic execution of replenishment orders
- **Warehouse Health Monitoring**: Track warehouse capacity, utilization, and operational status

---

## Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────────┐
│         Demand Forecasting System                           │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  ForecastResult                                         │ │
│  │  - Product SKU                                         │ │
│  │  - Forecasted Demand                                  │ │
│  │  - Standard Deviation                                 │ │
│  │  - Confidence Score                                   │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
        ┌──────────────────────────────────────┐
        │ ForecastToWarehouseIntegrationService │
        │ - Process forecasts                   │
        │ - Calculate replenishment quantities  │
        │ - Determine urgency levels           │
        │ - Execute orders (optional)          │
        └──────────────────────────────────────┘
                           │
                           ▼
        ┌──────────────────────────────────────┐
        │   IWarehouseAdapter (Interface)      │
        │   - getStockLevel()                  │
        │   - reserveStock()                   │
        │   - dispatchOrder()                  │
        │   - recordReplenishmentOrder()       │
        └──────────────────────────────────────┘
                           │
                           ▼
        ┌──────────────────────────────────────┐
        │ WarehouseManagementAdapter           │
        │ (Concrete Implementation)             │
        │ - Wraps WMS Facade                   │
        │ - Uses Reflection for Type Safety    │
        │ - Provides Logging & Error Handling  │
        └──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│         Warehouse Management System (WMS)                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  WarehouseFacade                                        │ │
│  │  ├─ InventoryManager                                  │ │
│  │  ├─ OrderPickingEngine                                │ │
│  │  └─ TaskEngine                                        │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Design Patterns

- **Adapter Pattern**: `IWarehouseAdapter` abstracts WMS operations
- **Facade Pattern**: `WarehouseFacade` simplifies WMS complexity
- **Dependency Injection**: Services receive adapters via constructor
- **Strategy Pattern**: WMS uses strategies for picking and putaway operations
- **Reflection-Based Binding**: Loose coupling via reflection to avoid hard JAR dependencies

---

## Components

### 1. IWarehouseAdapter Interface

**Location**: `src/main/java/com/forecast/integration/warehouse/IWarehouseAdapter.java`

Core interface defining warehouse operations contract.

**Key Methods**:
- `getStockLevel(String sku)` - Get current stock for a product
- `reserveStock(String sku, int quantity)` - Reserve stock for an order
- `dispatchOrder(String orderId, Map lineItems)` - Dispatch order to warehouse floor
- `receiveAndStoreProduct()` - Receive inbound inventory
- `recordReplenishmentOrder()` - Record purchase order
- `getWarehouseParameters()` - Get warehouse configuration
- `getWarehouseStatus()` - Get operational metrics

### 2. WarehouseManagementAdapter

**Location**: `src/main/java/com/forecast/integration/warehouse/WarehouseManagementAdapter.java`

Concrete implementation wrapping the WMS facade.

**Features**:
- Wraps `wms.services.WarehouseFacade`
- Uses reflection for runtime type binding
- Comprehensive logging and error handling
- Default fallback values for fault tolerance
- Transaction-like semantics for complex operations

**Key Methods**:
- All `IWarehouseAdapter` methods implemented
- Helper methods for WMS object creation via reflection
- Stock level queries with caching support

### 3. WarehouseParameters

**Location**: `src/main/java/com/forecast/integration/warehouse/WarehouseParameters.java`

Model encapsulating warehouse configuration and state.

**Properties**:
- Warehouse identification (ID, name, location)
- Capacity management (total, current utilization, thresholds)
- Operational parameters (lead time, safety stock, order limits)
- Performance metrics (picking time, receiving time, active tasks)
- Status indicators (operational flag, last update)

### 4. ForecastToWarehouseIntegrationService

**Location**: `src/main/java/com/forecast/services/warehouse/ForecastToWarehouseIntegrationService.java`

Service orchestrating forecast processing and warehouse operations.

**Key Responsibilities**:
- Process individual forecasts into replenishment actions
- Batch process multiple forecasts
- Calculate replenishment quantities using EOQ principles
- Determine urgency levels
- Execute replenishment orders
- Monitor warehouse health

**Inner Classes**:
- `ReplenishmentCalculation` - Holds calculation results
- `WarehouseReplenishmentAction` - Complete replenishment recommendation

### 5. WarehouseIntegrationExample

**Location**: `src/main/java/com/forecast/examples/WarehouseIntegrationExample.java`

Complete working example demonstrating all integration features.

**Scenarios Demonstrated**:
- Scenario 1: Critical stock level
- Scenario 2: High urgency replenishment
- Scenario 3: Medium urgency replenishment
- Scenario 4: Low urgency replenishment
- Scenario 5: Batch processing

---

## Installation & Setup

### Step 1: JAR Files in lib/

The warehouse management system JAR files should be copied to the `lib/` directory:

```
DemandForecasting/lib/
├── wms-database-module-1.0.0-SNAPSHOT-standalone.jar
├── wms-scm-exception-handler-v3.jar
└── wms-scm-exception-viewer-gui.jar
```

**Status**: ✅ Already copied from `src/main/warehouse/SCM-Subsystem-2-WMS/lib/`

### Step 2: Maven Dependencies

The `pom.xml` includes dependencies for all WMS JAR files:

```xml
<dependency>
    <groupId>com.wms</groupId>
    <artifactId>wms-database-module</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/wms-database-module-1.0.0-SNAPSHOT-standalone.jar</systemPath>
</dependency>

<dependency>
    <groupId>com.wms</groupId>
    <artifactId>wms-scm-exception-handler</artifactId>
    <version>3.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/wms-scm-exception-handler-v3.jar</systemPath>
</dependency>

<dependency>
    <groupId>com.wms</groupId>
    <artifactId>wms-scm-exception-viewer-gui</artifactId>
    <version>1.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/wms-scm-exception-viewer-gui.jar</systemPath>
</dependency>
```

### Step 3: Build Project

```bash
cd DemandForecasting
mvn clean compile
```

**Verification**:
```bash
mvn compile
# Should complete without errors
```

### Step 4: Run Example

```bash
mvn exec:java -Dexec.mainClass="com.forecast.examples.WarehouseIntegrationExample"
```

---

## Usage Guide

### Basic Usage Pattern

```java
// 1. Initialize warehouse adapter
IWarehouseAdapter adapter = new WarehouseManagementAdapter(warehouseFacade);

// 2. Create integration service
ForecastToWarehouseIntegrationService service = 
    new ForecastToWarehouseIntegrationService(adapter, autoExecute);

// 3. Create forecast result
ForecastResult forecast = new ForecastResult();
forecast.setProductSku("SKU-001");
forecast.setForecastedValue(500);
forecast.setStandardDeviation(50);
forecast.setConfidenceScore(0.95);

// 4. Process forecast
WarehouseReplenishmentAction action = service.processForecast(forecast);

// 5. Use results
System.out.println("Order Qty: " + action.getCalculatedOrderQuantity());
System.out.println("Urgency: " + action.getUrgencyLevel());
System.out.println("Status: " + action.getStatus());
```

### Batch Processing

```java
List<ForecastResult> forecasts = new ArrayList<>();
// ... populate forecasts ...

List<WarehouseReplenishmentAction> actions = 
    service.processForecastBatch(forecasts);

// Process results
for (WarehouseReplenishmentAction action : actions) {
    if ("CRITICAL".equals(action.getUrgencyLevel())) {
        // Handle critical replenishment
    }
}
```

### Query Warehouse Status

```java
// Check operational status
if (adapter.isWarehouseOperational()) {
    System.out.println(adapter.getWarehouseStatus());
}

// Get configuration
WarehouseParameters params = adapter.getWarehouseParameters();
System.out.println("Lead Time: " + params.getDefaultLeadTimeDays() + " days");
```

### Manual Order Execution

```java
// Process forecast (no auto-execute)
WarehouseReplenishmentAction action = service.processForecast(forecast);

// Review recommendation
if ("HIGH".equals(action.getUrgencyLevel())) {
    // Manually execute after review
    boolean success = adapter.recordReplenishmentOrder(
        action.getReplenishmentOrderId(),
        action.getProductSku(),
        action.getCalculatedOrderQuantity(),
        "VENDOR-123",
        action.getTargetDeliveryDate()
    );
}
```

---

## Configuration

### Warehouse Parameters

Configure warehouse-specific parameters in `WarehouseParameters`:

```java
WarehouseParameters params = adapter.getWarehouseParameters();

// Capacity
params.setTotalCapacity(50000);
params.setCurrentUtilization(15000);
params.setUtilizationThreshold(0.80);

// Lead Times
params.setDefaultLeadTimeDays(7);

// Safety Stock (demand variability factor)
params.setSafetyStockMultiplier(1.5);  // 1.5x std deviation

// Order Constraints
params.setMinimumOrderQuantity(50);    // Minimum order size
params.setMaximumOrderQuantity(10000); // Maximum order size
params.setReorderThreshold(500);       // Alert threshold

// Performance
params.setAveragePickingTimeMinutes(15);
params.setAverageReceivingTimeMinutes(30);
```

### Integration Service Configuration

```java
// Enable auto-execution for critical orders
service.setAutoExecuteReplenishment(true);

// Or use constructor parameter
ForecastToWarehouseIntegrationService service = 
    new ForecastToWarehouseIntegrationService(adapter, true);
```

### Recommended Settings by Warehouse Type

#### Small Warehouse
```
Lead Time: 3-5 days
Safety Stock Multiplier: 1.2
Min Order Qty: 10
Max Order Qty: 500
Reorder Threshold: 50
```

#### Medium Warehouse
```
Lead Time: 5-7 days
Safety Stock Multiplier: 1.5
Min Order Qty: 50
Max Order Qty: 5000
Reorder Threshold: 500
```

#### Large Distribution Center
```
Lead Time: 7-14 days
Safety Stock Multiplier: 2.0
Min Order Qty: 200
Max Order Qty: 50000
Reorder Threshold: 2000
```

---

## API Reference

### IWarehouseAdapter

#### getStockLevel(String sku)
Returns current stock quantity for a product.

**Parameters**:
- `sku`: Product stock keeping unit

**Returns**: Stock quantity (integer), or -1 if not found

**Example**:
```java
int stock = adapter.getStockLevel("SKU-001");
if (stock > 0) {
    System.out.println("Available stock: " + stock);
}
```

#### reserveStock(String sku, int quantity)
Reserves stock for a forecasted order.

**Parameters**:
- `sku`: Product SKU
- `quantity`: Quantity to reserve

**Returns**: true if successful, false otherwise

#### recordReplenishmentOrder(...)
Records a purchase order in the warehouse system.

**Parameters**:
- `orderId`: Replenishment order ID
- `sku`: Product SKU
- `quantity`: Order quantity
- `vendorId`: Supplier ID
- `targetDate`: Expected delivery date (YYYY-MM-DD)

**Returns**: true if successfully recorded

#### getWarehouseParameters()
Retrieves warehouse configuration.

**Returns**: `WarehouseParameters` object

#### getWarehouseStatus()
Gets detailed operational metrics.

**Returns**: String containing formatted status

#### isWarehouseOperational()
Checks if warehouse is available.

**Returns**: true if operational

### ForecastToWarehouseIntegrationService

#### processForecast(ForecastResult forecast)
Processes a single forecast into replenishment action.

**Parameters**:
- `forecast`: `ForecastResult` object

**Returns**: `WarehouseReplenishmentAction` with recommendations

**Throws**: Logs exceptions internally, returns error action

#### processForecastBatch(List<ForecastResult> forecasts)
Processes multiple forecasts simultaneously.

**Parameters**:
- `forecasts`: List of `ForecastResult` objects

**Returns**: List of `WarehouseReplenishmentAction` objects

#### setAutoExecuteReplenishment(boolean autoExecute)
Configures automatic order execution.

**Parameters**:
- `autoExecute`: true to auto-execute, false for manual review

### WarehouseReplenishmentAction

Data class containing complete replenishment recommendation.

**Key Properties**:
- `forecastId`: Source forecast ID
- `productSku`: Product identifier
- `currentStock`: Current warehouse stock
- `forecastedDemand`: Predicted demand
- `calculatedOrderQuantity`: Recommended order amount
- `urgencyLevel`: CRITICAL | HIGH | MEDIUM | LOW | NONE
- `safetyStock`: Calculated safety stock
- `reorderPoint`: Threshold for ordering
- `targetDeliveryDate`: Expected delivery date
- `status`: EXECUTED | PENDING_REVIEW | ERROR | etc.
- `rationale`: Human-readable explanation

---

## Examples

### Example 1: Simple Single-Product Forecast

```java
// Create forecast
ForecastResult forecast = new ForecastResult();
forecast.setForecastId("FORECAST-001");
forecast.setProductSku("SKU-LAPTOP");
forecast.setForecastedValue(150);
forecast.setStandardDeviation(15);
forecast.setConfidenceScore(0.92);

// Process
WarehouseReplenishmentAction action = service.processForecast(forecast);

// Display recommendation
System.out.println("Product: " + action.getProductSku());
System.out.println("Current Stock: " + action.getCurrentStock());
System.out.println("Order Quantity: " + action.getCalculatedOrderQuantity());
System.out.println("Urgency: " + action.getUrgencyLevel());
```

### Example 2: Processing Multiple SKUs with Different Priorities

```java
List<ForecastResult> forecasts = Arrays.asList(
    createForecast("FORECAST-A", "SKU-A", 500, 50),
    createForecast("FORECAST-B", "SKU-B", 300, 30),
    createForecast("FORECAST-C", "SKU-C", 200, 20)
);

List<WarehouseReplenishmentAction> actions = 
    service.processForecastBatch(forecasts);

// Separate by urgency
Map<String, List<WarehouseReplenishmentAction>> byUrgency = 
    actions.stream()
        .collect(Collectors.groupingBy(WarehouseReplenishmentAction::getUrgencyLevel));

// Process critical items first
byUrgency.getOrDefault("CRITICAL", Collections.emptyList())
    .forEach(action -> handleCriticalOrder(action));
```

### Example 3: Custom Replenishment Logic

```java
ForecastToWarehouseIntegrationService service = 
    new ForecastToWarehouseIntegrationService(adapter, false);

for (ForecastResult forecast : forecasts) {
    WarehouseReplenishmentAction action = service.processForecast(forecast);
    
    // Custom business logic
    if ("CRITICAL".equals(action.getUrgencyLevel())) {
        // Notify urgent action needed
        notifyAlert(action);
        
        // Execute immediately
        adapter.recordReplenishmentOrder(
            action.getReplenishmentOrderId(),
            action.getProductSku(),
            action.getCalculatedOrderQuantity(),
            selectBestVendor(action.getProductSku()),
            action.getTargetDeliveryDate()
        );
    } else if ("HIGH".equals(action.getUrgencyLevel())) {
        // Add to queue for manual approval
        approvalQueue.add(action);
    }
}
```

### Example 4: Monitoring Warehouse Health

```java
// Check before processing
if (!adapter.isWarehouseOperational()) {
    logger.warning("Warehouse unavailable, deferring forecast processing");
    return;
}

WarehouseParameters params = adapter.getWarehouseParameters();

// Check capacity
if (params.isNearCapacity()) {
    logger.warning("Warehouse at " + 
        String.format("%.1f", params.getUtilizationPercentage()) + 
        "% capacity");
    // Implement capacity-aware ordering
}

// Check active tasks
if (params.getActiveTaskCount() > 100) {
    logger.info("Warehouse busy with " + params.getActiveTaskCount() + 
        " active tasks");
    // May want to defer non-critical orders
}

System.out.println(adapter.getWarehouseStatus());
```

---

## Troubleshooting

### Issue: "WMS classes not available on classpath"

**Cause**: WMS JAR files not in lib/ directory or not properly configured in pom.xml

**Solution**:
1. Verify JAR files are in `DemandForecasting/lib/`:
   ```bash
   ls -la lib/wms-*.jar
   ```

2. Rebuild Maven:
   ```bash
   mvn clean compile
   ```

3. Check pom.xml paths are correct

### Issue: Warehouse Adapter Returns -1 for Stock Levels

**Cause**: WMS InventoryManager not properly initialized or stock not recorded

**Solution**:
1. Verify warehouse facade is properly initialized:
   ```java
   if (adapter.isWarehouseOperational()) {
       // Safe to proceed
   }
   ```

2. Check WMS database connectivity and data population

3. Ensure products are registered in WMS before querying stock

### Issue: "Cannot instantiate WMS Order/Product classes"

**Cause**: Reflection-based class loading failed due to:
- Missing WMS classes on classpath
- Incorrect class names or packages
- Constructor signature mismatch

**Solution**:
1. Verify WMS JAR files are included in build classpath
2. Check WMS class names match those in JAR (e.g., `wms.models.Order`)
3. Enable debug logging to see detailed reflection errors:
   ```java
   Logger.getLogger("com.forecast.integration.warehouse")
       .setLevel(Level.FINE);
   ```

### Issue: All Forecasts Return "WAREHOUSE_UNAVAILABLE"

**Cause**: 
- Warehouse adapter not initialized
- WMS facade connection lost
- Warehouse marked as non-operational

**Solution**:
1. Check adapter initialization:
   ```java
   IWarehouseAdapter adapter = new WarehouseManagementAdapter(warehouseFacade);
   if (adapter == null || !adapter.isWarehouseOperational()) {
       // Handle initialization failure
   }
   ```

2. Verify WMS is running and responding

3. Check warehouse parameters configuration

### Issue: Order Quantities Always Zero

**Cause**:
- Current stock above reorder point
- Parameters set too conservatively
- Forecasted demand too low

**Solution**:
1. Check current stock levels:
   ```java
   int stock = adapter.getStockLevel(sku);
   System.out.println("Current stock: " + stock);
   ```

2. Verify reorder point calculation:
   ```java
   WarehouseParameters params = adapter.getWarehouseParameters();
   int reorderThreshold = params.getReorderThreshold();
   ```

3. Review forecast demand values:
   ```java
   System.out.println("Forecasted: " + forecast.getForecastedValue());
   ```

4. Adjust parameters if too conservative

---

## Best Practices

### 1. Always Verify Warehouse Operational Status

```java
if (!adapter.isWarehouseOperational()) {
    // Queue forecasts for later processing
    // Don't fail, defer gracefully
    logger.info("Warehouse temporarily unavailable, deferring processing");
    return;
}
```

### 2. Implement Tiered Review Process

```java
// Manual review for high-value decisions
if ("CRITICAL".equals(action.getUrgencyLevel())) {
    notifyManagementApproval(action);  // Requires approval
} else if ("HIGH".equals(action.getUrgencyLevel())) {
    recordOrder(action);  // Auto-execute, notify
} else {
    queueForBatchProcessing(action);  // Process with others
}
```

### 3. Log All Replenishment Decisions

```java
logger.info("Forecast processed: SKU=" + action.getProductSku() +
           ", Urgency=" + action.getUrgencyLevel() +
           ", OrderQty=" + action.getCalculatedOrderQuantity() +
           ", Executed=" + action.isExecuted());
```

### 4. Tune Parameters Based on Actual Performance

Use historical data to calibrate:
- Safety stock multiplier (target 95-99% service level)
- Lead times (track actual vs. expected)
- Min/Max order quantities (match vendor minimums and warehouse capacity)
- Reorder thresholds (balance carrying costs vs. stockouts)

### 5. Monitor Forecast Accuracy Impact

Track:
- Standard deviation of actual vs. forecast
- Confidence scores vs. accuracy
- Urgency vs. actual inventory outcomes
- Adjustment factors over time

### 6. Handle Edge Cases

```java
// Handle null forecasts
if (forecast == null || forecast.getProductSku() == null) {
    logger.warning("Invalid forecast, skipping");
    continue;
}

// Handle extreme values
if (forecast.getForecastedValue() < 0) {
    logger.warning("Negative forecast, treating as zero");
    forecast.setForecastedValue(0);
}

// Handle missing standard deviation
if (forecast.getStandardDeviation() <= 0) {
    forecast.setStandardDeviation(forecast.getForecastedValue() * 0.2);
}
```

### 7. Use Batch Processing for Efficiency

```java
// Good: Process batch
List<WarehouseReplenishmentAction> actions = 
    service.processForecastBatch(forecasts);  // Single query batch

// Avoid: Individual processing in loop
for (ForecastResult forecast : forecasts) {
    service.processForecast(forecast);  // N queries
}
```

### 8. Implement Graceful Degradation

```java
try {
    // Try to use warehouse adapter for real-time data
    action = service.processForecast(forecast);
} catch (Exception e) {
    // Fall back to reasonable defaults
    logger.warning("WMS integration failed, using default parameters");
    action = generateDefaultAction(forecast);
}
```

### 9. Separate Concerns

- **Forecast Processing**: Use `ForecastResult` objects
- **Replenishment Logic**: Use `WarehouseReplenishmentAction` objects
- **Warehouse Operations**: Use `IWarehouseAdapter` interface
- **Configuration**: Use `WarehouseParameters` objects

### 10. Test Integration Points

```java
// Test adapter connection
@Test
public void testWarehouseConnection() {
    assertTrue(adapter.isWarehouseOperational());
}

// Test forecast processing
@Test
public void testForecastProcessing() {
    ForecastResult forecast = createTestForecast();
    WarehouseReplenishmentAction action = service.processForecast(forecast);
    assertNotNull(action.getReplenishmentOrderId());
}

// Test batch processing
@Test
public void testBatchProcessing() {
    List<WarehouseReplenishmentAction> actions = 
        service.processForecastBatch(testForecasts);
    assertEquals(testForecasts.size(), actions.size());
}
```

---

## Integration with Existing Forecast System

The warehouse integration is designed to work seamlessly with the existing forecast system:

### ForecastOutputService Enhancement

The `ForecastOutputService` can be extended to automatically generate warehouse recommendations:

```java
@Override
public void publishForecast(ForecastResult forecast) {
    // Existing logic
    super.publishForecast(forecast);
    
    // New: Generate warehouse recommendations
    if (warehouseIntegrationService != null) {
        WarehouseReplenishmentAction action = 
            warehouseIntegrationService.processForecast(forecast);
        
        // Store or alert based on action
        handleWarehouseAction(action);
    }
}
```

---

## Performance Considerations

- **Single Forecast**: ~50-100ms (including reflection overhead)
- **Batch Processing**: ~10-20ms per forecast (amortized)
- **Stock Queries**: ~20-50ms per SKU
- **Warehouse Status**: ~30-100ms

### Optimization Tips

1. **Cache warehouse parameters** if they don't change frequently
2. **Batch queries** using `getMultipleStockLevels()` for multiple SKUs
3. **Defer non-critical orders** to off-peak hours
4. **Use async processing** for large forecast batches
5. **Implement connection pooling** at WMS level

---

## Support and Maintenance

For issues or questions:

1. Check `WAREHOUSE_INTEGRATION_GUIDE.md` (this file)
2. Review examples in `WarehouseIntegrationExample.java`
3. Enable debug logging in integration service
4. Verify WMS JAR files and classpath configuration
5. Check warehouse operational status via adapter

---

**Last Updated**: 2024
**Version**: 1.0
**Status**: Production Ready
