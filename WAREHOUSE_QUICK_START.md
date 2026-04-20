# Warehouse Integration Quick Start Guide

## ⚡ 5-Minute Setup

### Prerequisites
- Java 11+
- Maven 3.6+
- Project compiled: `mvn clean compile`

### Step 1: Verify JAR Files
WMS JAR files should already be in `lib/`:
```bash
ls DemandForecasting/lib/wms-*.jar
# Should show 3 files
```

### Step 2: Build Project
```bash
cd DemandForecasting
mvn clean compile
```

### Step 3: Run Example
```bash
mvn exec:java -Dexec.mainClass="com.forecast.examples.WarehouseIntegrationExample"
```

Expected output: 5 scenario demonstrations showing replenishment recommendations.

---

## 🚀 Basic Usage (Copy-Paste Ready)

### Initialize and Process a Forecast

```java
import com.forecast.integration.warehouse.WarehouseManagementAdapter;
import com.forecast.integration.warehouse.IWarehouseAdapter;
import com.forecast.models.ForecastResult;
import com.forecast.services.warehouse.ForecastToWarehouseIntegrationService;

public class MyWarehouseIntegration {
    public static void main(String[] args) {
        // 1. Initialize adapter (pass WMS facade or null for demo)
        IWarehouseAdapter adapter = new WarehouseManagementAdapter(null);
        
        // 2. Create integration service
        ForecastToWarehouseIntegrationService service = 
            new ForecastToWarehouseIntegrationService(adapter);
        
        // 3. Create a forecast
        ForecastResult forecast = new ForecastResult();
        forecast.setForecastId("FC-001");
        forecast.setProductSku("SKU-LAPTOP");
        forecast.setForecastedValue(500);          // Expected demand
        forecast.setStandardDeviation(50);         // Demand variability
        forecast.setConfidenceScore(0.95);         // 95% confidence
        
        // 4. Process forecast
        var action = service.processForecast(forecast);
        
        // 5. Use results
        System.out.println("Product: " + action.getProductSku());
        System.out.println("Order Qty: " + action.getCalculatedOrderQuantity());
        System.out.println("Urgency: " + action.getUrgencyLevel());
        System.out.println("Status: " + action.getStatus());
    }
}
```

---

## 📊 Key Concepts

### Urgency Levels
- **CRITICAL**: Less than 1 day of stock remaining
- **HIGH**: Below reorder point, needs immediate order
- **MEDIUM**: Close to reorder point
- **LOW**: Adequate stock levels
- **NONE**: Healthy inventory

### Replenishment Action Fields
```
forecastId              → Source forecast identifier
productSku              → Product code
currentStock            → Current warehouse stock
forecastedDemand        → Predicted demand
calculatedOrderQuantity → Recommended order amount
urgencyLevel            → Priority level
safetyStock             → Buffer stock amount
reorderPoint            → Trigger point for ordering
targetDeliveryDate      → Expected arrival date
status                  → EXECUTED | PENDING_REVIEW | ERROR
```

---

## 🔧 Common Scenarios

### Scenario 1: Process Single Forecast
```java
ForecastResult forecast = createForecast("SKU-001", 300, 30);
var action = service.processForecast(forecast);
System.out.println("Order: " + action.getCalculatedOrderQuantity());
```

### Scenario 2: Batch Process Multiple Products
```java
List<ForecastResult> forecasts = Arrays.asList(
    createForecast("SKU-A", 500, 50),
    createForecast("SKU-B", 300, 30),
    createForecast("SKU-C", 200, 20)
);

List<var> actions = service.processForecastBatch(forecasts);

// Process critical items first
actions.stream()
    .filter(a -> "CRITICAL".equals(a.getUrgencyLevel()))
    .forEach(this::handleCriticalOrder);
```

### Scenario 3: Auto-Execute Critical Orders
```java
// Enable auto-execution
service.setAutoExecuteReplenishment(true);

var action = service.processForecast(forecast);
if (action.isExecuted()) {
    System.out.println("Order placed: " + action.getReplenishmentOrderId());
}
```

### Scenario 4: Manual Review Workflow
```java
// Process without auto-executing
service.setAutoExecuteReplenishment(false);

var action = service.processForecast(forecast);
if ("HIGH".equals(action.getUrgencyLevel())) {
    // Review and approve manually
    boolean approved = approveOrder(action);
    if (approved) {
        adapter.recordReplenishmentOrder(
            action.getReplenishmentOrderId(),
            action.getProductSku(),
            action.getCalculatedOrderQuantity(),
            "VENDOR-123",
            action.getTargetDeliveryDate()
        );
    }
}
```

### Scenario 5: Check Warehouse Health
```java
if (!adapter.isWarehouseOperational()) {
    System.out.println("Warehouse unavailable, deferring processing");
    return;
}

var params = adapter.getWarehouseParameters();
System.out.println("Capacity: " + params.getUtilizationPercentage() + "%");
System.out.println("Lead Time: " + params.getDefaultLeadTimeDays() + " days");
System.out.println(adapter.getWarehouseStatus());
```

---

## 📁 File Structure

```
DemandForecasting/
├── lib/
│   ├── wms-database-module-1.0.0-SNAPSHOT-standalone.jar
│   ├── wms-scm-exception-handler-v3.jar
│   └── wms-scm-exception-viewer-gui.jar
├── src/main/java/com/forecast/
│   ├── integration/warehouse/
│   │   ├── IWarehouseAdapter.java                 (Interface)
│   │   ├── WarehouseManagementAdapter.java        (Implementation)
│   │   └── WarehouseParameters.java               (Config model)
│   ├── services/warehouse/
│   │   └── ForecastToWarehouseIntegrationService.java
│   └── examples/
│       └── WarehouseIntegrationExample.java       (See scenarios)
├── WAREHOUSE_INTEGRATION_GUIDE.md                 (Full documentation)
└── WAREHOUSE_QUICK_START.md                       (This file)
```

---

## ⚙️ Configuration Defaults

```java
WarehouseParameters params = adapter.getWarehouseParameters();

// Default values (adjustable):
params.getDefaultLeadTimeDays()      // 7 days
params.getSafetyStockMultiplier()    // 1.5x standard deviation
params.getMinimumOrderQuantity()     // 50 units
params.getMaximumOrderQuantity()     // 10000 units
params.getReorderThreshold()         // 500 units
params.getTotalCapacity()            // 50000 units
```

To customize:
```java
params.setDefaultLeadTimeDays(3);
params.setSafetyStockMultiplier(2.0);
params.setMinimumOrderQuantity(100);
```

---

## 🧪 Quick Test

Run this to verify everything works:

```java
@Test
public void testWarehouseIntegration() {
    IWarehouseAdapter adapter = new WarehouseManagementAdapter(null);
    ForecastToWarehouseIntegrationService service = 
        new ForecastToWarehouseIntegrationService(adapter);
    
    ForecastResult forecast = new ForecastResult();
    forecast.setForecastId("TEST-001");
    forecast.setProductSku("TEST-SKU");
    forecast.setForecastedValue(100);
    forecast.setStandardDeviation(10);
    forecast.setConfidenceScore(0.90);
    
    var action = service.processForecast(forecast);
    
    assertNotNull(action);
    assertNotNull(action.getReplenishmentOrderId());
    assertNotNull(action.getUrgencyLevel());
}
```

---

## 📝 Helper Method

```java
// Create forecast quickly
private ForecastResult createForecast(String sku, int demand, int stdDev) {
    ForecastResult f = new ForecastResult();
    f.setForecastId("FC-" + System.currentTimeMillis());
    f.setProductSku(sku);
    f.setForecastedValue(demand);
    f.setStandardDeviation(stdDev);
    f.setConfidenceScore(0.90);
    return f;
}
```

---

## 🔗 Integration Points

### With ForecastOutputService
```java
// Enhance existing forecast publishing
public void publishForecast(ForecastResult forecast) {
    // Existing: persist forecast
    super.publishForecast(forecast);
    
    // New: generate warehouse action
    var action = warehouseService.processForecast(forecast);
    if ("CRITICAL".equals(action.getUrgencyLevel())) {
        alertManager.notifyUrgent(action);
    }
}
```

---

## ⚠️ Troubleshooting

### "WMS classes not available"
```bash
# Verify JAR files
ls lib/wms-*.jar

# Rebuild
mvn clean compile
```

### Stock levels return -1
- Warehouse not operational: check `adapter.isWarehouseOperational()`
- WMS database not initialized
- Product not registered in WMS

### Orders always quantity 0
- Current stock above reorder point (normal)
- Forecasted demand too low
- Adjust `params.setReorderThreshold()` if needed

### "Warehouse adapter not initialized"
- Pass actual WMS facade object, not null
- Or verify null adapter is intentional (demo mode)

---

## 📚 Next Steps

1. **Review Full Guide**: `WAREHOUSE_INTEGRATION_GUIDE.md` for complete API reference
2. **Run Example**: `WarehouseIntegrationExample.java` shows all scenarios
3. **Customize Parameters**: Adjust `WarehouseParameters` for your warehouse
4. **Enable Auto-Execute**: For production after testing
5. **Monitor Performance**: Track accuracy and adjust safety stock

---

## 🎯 Essential API Calls

```java
// Query
adapter.getStockLevel("SKU-001");
adapter.getWarehouseStatus();
adapter.isWarehouseOperational();

// Process
service.processForecast(forecast);
service.processForecastBatch(forecasts);

// Execute
adapter.recordReplenishmentOrder(orderId, sku, qty, vendor, date);

// Configure
service.setAutoExecuteReplenishment(true/false);
params.setDefaultLeadTimeDays(X);
```

---

**Ready to go!** Start with the example, then integrate into your forecast workflow.

For detailed reference, see `WAREHOUSE_INTEGRATION_GUIDE.md`.