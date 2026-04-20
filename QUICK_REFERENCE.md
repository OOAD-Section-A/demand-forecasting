# Inventory Integration - Quick Reference Guide

## 30-Second Setup

```java
// 1. Create adapter
IInventoryAdapter adapter = new InventoryManagementAdapter(new InventoryService());

// 2. Create integration service
InventoryIntegrationService integration = 
    new InventoryIntegrationService(adapter, exceptionSource);

// 3. Update forecast output service
ForecastOutputService outputService = new ForecastOutputService(
    exceptionSource, persistenceService, adapter, false);
outputService.setInventoryIntegrationEnabled(true);
```

## Common Operations

### Generate Single Recommendation
```java
ReplenishmentRecommendation rec = 
    integration.generateRecommendationForLocation(forecast, "WAREHOUSE_A");

System.out.println("Urgency: " + rec.getUrgencyLevel());
System.out.println("Order Qty: " + rec.getRecommendedOrderQuantity());
```

### Batch Generate Recommendations
```java
List<ReplenishmentRecommendation> recs = 
    integration.processBatchForecasts(forecasts, "WAREHOUSE_A");

// Filter critical items
recs.stream()
    .filter(r -> "CRITICAL".equals(r.getUrgencyLevel()))
    .forEach(r -> System.out.println(r));
```

### Execute Order
```java
boolean success = integration.executeReplenishmentOrder(
    recommendation, 
    "SUPPLIER_ID", 
    "PO_REF_001"
);
```

### Query Inventory State
```java
InventoryParameters params = 
    integration.getInventoryParameters(productId, locationId);

System.out.println("Current: " + params.getCurrentStockLevel());
System.out.println("Safety: " + params.getSafetyStockLevel());
```

## Urgency Levels

| Level    | Meaning | Action |
|----------|---------|--------|
| CRITICAL | Stock ≤ safety level | Execute immediately |
| HIGH     | Stock ≤ reorder point | Execute soon |
| MEDIUM   | Stock < 1 week demand | Consider ordering |
| LOW      | Adequate stock | Routine order |

## Key Classes

| Class | Purpose |
|-------|---------|
| `IInventoryAdapter` | Inventory operations contract |
| `InventoryManagementAdapter` | Wraps InventoryService |
| `InventoryIntegrationService` | Orchestrates forecast-inventory workflow |
| `ReplenishmentRecommendation` | Output model |
| `InventoryParameters` | Inventory state/config model |

## File Locations

```
com/forecast/
├── integration/inventory/
│   ├── IInventoryAdapter.java
│   ├── InventoryManagementAdapter.java
│   └── InventoryParameters.java
├── models/inventory/
│   └── ReplenishmentRecommendation.java
└── services/inventory/
    └── InventoryIntegrationService.java
```

## Alert Codes

```
550 - Inventory system unavailable
551 - Failed to generate recommendation
552 - Failed to record order
553 - Order execution exception
554 - Critical stock detected
558 - Store ID missing
559 - Failed to generate recommendations
560 - Critical inventory alert
```

## Enable/Disable

```java
// Enable inventory integration
outputService.setInventoryIntegrationEnabled(true);

// Disable inventory integration
outputService.setInventoryIntegrationEnabled(false);

// Check status
boolean enabled = outputService.isInventoryIntegrationEnabled();
```

## Auto-Execute (Use Carefully)

```java
// Enable auto-execution of critical orders
ForecastOutputService outputService = new ForecastOutputService(
    exceptionSource, persistenceService, adapter, true);
```

## Error Handling

```java
try {
    ReplenishmentRecommendation rec = 
        integration.generateRecommendationForLocation(forecast, location);
    
    if (rec == null) {
        LOGGER.warning("Product not found in inventory");
        return;
    }
    
    // Process recommendation
} catch (ForecastingException e) {
    LOGGER.log(Level.SEVERE, "Forecasting error", e);
} catch (Exception e) {
    LOGGER.log(Level.SEVERE, "Unexpected error", e);
}
```

## Check Inventory System Available

```java
if (!adapter.isInventorySystemAvailable()) {
    LOGGER.warning("Inventory system offline");
    // Handle gracefully - forecasts still published
} else {
    // Recommendations can be generated
}
```

## Customize Thresholds

### Override in InventoryManagementAdapter
```java
@Override
public int getSafetyStockLevel(String productId) {
    // Custom logic
    return calculateCustomSafetyStock(productId);
}

@Override
public int getLeadTime(String productId) {
    // Custom logic
    return getProductLeadTime(productId);
}
```

## Batch Processing Best Practices

```java
// Good: Process multiple products together
List<ForecastResult> forecasts = generateForecasts(productIds);
List<ReplenishmentRecommendation> recs = 
    integration.processBatchForecasts(forecasts, warehouse);

// Then filter and process
recs.stream()
    .filter(r -> "CRITICAL".equals(r.getUrgencyLevel()))
    .forEach(this::executeOrder);
```

## Recommendation Fields

```
ReceivedOrderQuantity - How many units to order
UrgencyLevel - CRITICAL/HIGH/MEDIUM/LOW
RecommendedOrderDate - When to place order
RecommendedDeliveryDate - Expected delivery
ForecastedAverageDemand - Avg demand in forecast
DaysUntilStockout - Estimated days remaining
Confidence - 0.0 to 1.0 confidence score
Rationale - Explanation of recommendation
Status - PENDING/APPROVED/REJECTED/EXECUTED
```

## Testing a Recommendation

```java
// Create test forecast
ForecastResult forecast = new ForecastResult();
forecast.setProductId("TEST_SKU");
forecast.setStoreId("TEST_STORE");
forecast.setForecastedDemand(Arrays.asList(
    BigDecimal.valueOf(100),
    BigDecimal.valueOf(110),
    BigDecimal.valueOf(105)
));

// Generate recommendation
ReplenishmentRecommendation rec = 
    integration.generateRecommendationForLocation(forecast, "TEST_WAREHOUSE");

// Inspect result
if (rec != null) {
    System.out.println("Qty: " + rec.getRecommendedOrderQuantity());
    System.out.println("Urgency: " + rec.getUrgencyLevel());
}
```

## Integration with Existing Forecast Flow

```java
// Existing code - no changes needed
ForecastResult result = forecastProcessor.processForecast(
    productId, storeId, features, lifecycle);

// Enhanced - now includes inventory integration
forecastOutputService.publishForecast(result);
// ^ Automatically generates inventory recommendations
```

## Common Mistakes to Avoid

1. ❌ Forgetting to enable integration
   ```java
   // Missing this!
   outputService.setInventoryIntegrationEnabled(true);
   ```

2. ❌ Not handling null recommendations
   ```java
   ReplenishmentRecommendation rec = generateRecommendation(...);
   if (rec == null) { /* Handle */ }  // Important!
   ```

3. ❌ Not checking inventory system availability
   ```java
   if (!adapter.isInventorySystemAvailable()) { /* Handle */ }
   ```

4. ❌ Executing orders without review
   ```java
   // Be careful with auto-execution
   // Test thoroughly first
   ```

## Documentation

- **INVENTORY_INTEGRATION_GUIDE.md** - Full reference (583 lines)
- **InventoryIntegrationExample.java** - Working examples (416 lines)
- **INTEGRATION_SUMMARY.md** - Architecture overview (359 lines)
- **QUICK_REFERENCE.md** - This file

## Example Execution

```java
// See InventoryIntegrationExample.java for complete examples:
// - Example 1: Basic forecasting with auto-integration
// - Example 2: Manual recommendation generation
// - Example 3: Batch processing
// - Example 4: Query inventory parameters
// - Example 5: Error handling

// Run:
// java com.forecast.examples.InventoryIntegrationExample
```

## Support Resources

| Resource | For |
|----------|-----|
| INVENTORY_INTEGRATION_GUIDE.md | Deep technical details |
| InventoryIntegrationExample.java | Working code patterns |
| INTEGRATION_SUMMARY.md | Architecture overview |
| IInventoryAdapter.java | API reference |
| InventoryIntegrationService.java | Implementation details |

## Performance Tips

1. **Use batch processing** for multiple products
2. **Cache inventory parameters** if querying repeatedly
3. **Filter critical items** before executing orders
4. **Monitor log levels** - set to INFO for production
5. **Use recommendation history** to track trends

## Key Takeaway

The inventory integration is **automatic and transparent**:
1. Generate forecast
2. Call `publishForecast()`
3. Recommendations generated automatically
4. Review and execute as needed

No changes to existing forecast code required!