package com.forecast.integration.warehouse;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Concrete implementation of the warehouse adapter.
 * Wraps the Warehouse Management System (WMS) facade and provides
 * integration between the demand forecasting system and the WMS.
 *
 * @author Demand Forecasting Team
 * @version 1.0
 */
public class WarehouseManagementAdapter implements IWarehouseAdapter {

    private static final Logger logger = Logger.getLogger(WarehouseManagementAdapter.class.getName());

    private Object warehouseFacade; // wms.services.WarehouseFacade
    private WarehouseParameters parameters;
    private boolean connected;

    /**
     * Constructor accepting a WMS facade instance.
     * The facade is passed as Object to avoid direct dependency on WMS classes
     * that may only be available via JAR at runtime.
     *
     * @param warehouseFacade The WMS WarehouseFacade instance
     */
    public WarehouseManagementAdapter(Object warehouseFacade) {
        this.warehouseFacade = warehouseFacade;
        this.connected = warehouseFacade != null;
        this.parameters = new WarehouseParameters();
        initializeParameters();
        logger.info("WarehouseManagementAdapter initialized with facade: " +
                   (warehouseFacade != null ? warehouseFacade.getClass().getName() : "null"));
    }

    /**
     * Initialize warehouse parameters from the WMS.
     * Sets default values if WMS doesn't provide them.
     */
    private void initializeParameters() {
        if (!connected) {
            logger.warning("Warehouse facade not connected. Using default parameters.");
            return;
        }

        try {
            // Set default/discovered parameters
            parameters.setWarehouseId("WH-001");
            parameters.setWarehouseName("Primary Warehouse");
            parameters.setLocation("Distribution Center");
            parameters.setTotalCapacity(50000);
            parameters.setCurrentUtilization(0);
            parameters.setDefaultLeadTimeDays(7);
            parameters.setSafetyStockMultiplier(1.5);
            parameters.setMinimumOrderQuantity(50);
            parameters.setMaximumOrderQuantity(10000);
            parameters.setReorderThreshold(500);
            parameters.setOperational(true);
            logger.info("Warehouse parameters initialized: " + parameters.toString());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error initializing warehouse parameters", e);
        }
    }

    @Override
    public int getStockLevel(String sku) {
        if (!connected || sku == null || sku.isEmpty()) {
            logger.warning("Cannot get stock level: facade connected=" + connected + ", sku=" + sku);
            return -1;
        }

        try {
            // Try to access InventoryManager through reflection
            Object inventoryManager = getInventoryManager();
            if (inventoryManager == null) {
                logger.warning("InventoryManager not available for SKU: " + sku);
                return -1;
            }

            // Attempt to call getStock method via reflection
            java.lang.reflect.Method getStockMethod = inventoryManager.getClass()
                    .getMethod("getStock", String.class);
            Integer stock = (Integer) getStockMethod.invoke(inventoryManager, sku);

            int level = stock != null ? stock : 0;
            logger.fine("Stock level for SKU " + sku + ": " + level);
            return level;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error retrieving stock level for SKU: " + sku, e);
            return -1;
        }
    }

    @Override
    public Map<String, Integer> getMultipleStockLevels(String... skus) {
        Map<String, Integer> stockLevels = new HashMap<>();

        if (skus == null || skus.length == 0) {
            logger.warning("No SKUs provided for stock level query");
            return stockLevels;
        }

        for (String sku : skus) {
            int level = getStockLevel(sku);
            stockLevels.put(sku, level >= 0 ? level : 0);
        }

        logger.fine("Retrieved stock levels for " + skus.length + " SKUs");
        return stockLevels;
    }

    @Override
    public boolean reserveStock(String sku, int quantity) {
        if (!connected || sku == null || sku.isEmpty() || quantity <= 0) {
            logger.warning("Cannot reserve stock: facade connected=" + connected +
                         ", sku=" + sku + ", quantity=" + quantity);
            return false;
        }

        try {
            Object inventoryManager = getInventoryManager();
            if (inventoryManager == null) {
                logger.warning("InventoryManager not available for stock reservation");
                return false;
            }

            // Try to call reserveStock method
            java.lang.reflect.Method reserveMethod = inventoryManager.getClass()
                    .getMethod("reserveStock", String.class, int.class);
            Boolean result = (Boolean) reserveMethod.invoke(inventoryManager, sku, quantity);

            boolean reserved = result != null && result;
            logger.info("Stock reservation for SKU " + sku + " (qty: " + quantity + "): " +
                       (reserved ? "SUCCESS" : "FAILED"));
            return reserved;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error reserving stock for SKU: " + sku, e);
            return false;
        }
    }

    @Override
    public boolean dispatchOrder(String orderId, Map<String, Integer> lineItems) {
        if (!connected || orderId == null || orderId.isEmpty() || lineItems == null || lineItems.isEmpty()) {
            logger.warning("Cannot dispatch order: invalid parameters");
            return false;
        }

        try {
            // Create WMS Order object
            Object wmsOrder = createWMSOrder(orderId, lineItems);
            if (wmsOrder == null) {
                logger.warning("Failed to create WMS Order for dispatch");
                return false;
            }

            // Call dispatchOrder on facade with a default strategy
            java.lang.reflect.Method dispatchMethod = warehouseFacade.getClass()
                    .getMethod("dispatchOrder", wmsOrder.getClass(),
                              Class.forName("wms.strategies.IPickingStrategy"));

            // Use default/standard picking strategy
            Object defaultStrategy = getDefaultPickingStrategy();
            Boolean result = (Boolean) dispatchMethod.invoke(warehouseFacade, wmsOrder, defaultStrategy);

            boolean dispatched = result != null && result;
            logger.info("Order dispatch for order ID " + orderId + ": " +
                       (dispatched ? "SUCCESS" : "FAILED"));
            return dispatched;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error dispatching order: " + orderId, e);
            return false;
        }
    }

    @Override
    public boolean receiveAndStoreProduct(String sku, String productName, int quantity, String category) {
        if (!connected || sku == null || quantity <= 0) {
            logger.warning("Cannot receive product: invalid parameters");
            return false;
        }

        try {
            // Create WMS Product object
            Object wmsProduct = createWMSProduct(sku, productName, category);
            if (wmsProduct == null) {
                logger.warning("Failed to create WMS Product for receiving");
                return false;
            }

            // Call receiveAndStoreProduct on facade
            java.lang.reflect.Method receiveMethod = warehouseFacade.getClass()
                    .getMethod("receiveAndStoreProduct", wmsProduct.getClass(), int.class);
            receiveMethod.invoke(warehouseFacade, wmsProduct, quantity);

            logger.info("Product received and stored: SKU=" + sku + ", Qty=" + quantity);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error receiving and storing product SKU: " + sku, e);
            return false;
        }
    }

    @Override
    public boolean processCrossDock(String sku, int quantity, String outboundOrderId) {
        if (!connected || sku == null || quantity <= 0 || outboundOrderId == null) {
            logger.warning("Cannot process cross-dock: invalid parameters");
            return false;
        }

        try {
            // Create WMS Product object
            Object wmsProduct = createWMSProduct(sku, "Cross-dock Product", "STANDARD");
            if (wmsProduct == null) {
                logger.warning("Failed to create WMS Product for cross-docking");
                return false;
            }

            // Call processCrossDock on facade
            java.lang.reflect.Method crossDockMethod = warehouseFacade.getClass()
                    .getMethod("processCrossDock", wmsProduct.getClass(), int.class, String.class);
            crossDockMethod.invoke(warehouseFacade, wmsProduct, quantity, outboundOrderId);

            logger.info("Cross-dock processed: SKU=" + sku + ", OrderID=" + outboundOrderId);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error processing cross-dock for SKU: " + sku, e);
            return false;
        }
    }

    @Override
    public boolean isWarehouseOperational() {
        if (!connected) {
            return false;
        }

        try {
            // Check if facade is accessible and functional
            if (warehouseFacade != null && getInventoryManager() != null) {
                parameters.setOperational(true);
                return true;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Warehouse operational check failed", e);
        }

        parameters.setOperational(false);
        return false;
    }

    @Override
    public WarehouseParameters getWarehouseParameters() {
        return parameters;
    }

    @Override
    public boolean recordReplenishmentOrder(String orderId, String sku, int quantity,
                                           String vendorId, String targetDate) {
        if (!connected || orderId == null || sku == null || quantity <= 0) {
            logger.warning("Cannot record replenishment order: invalid parameters");
            return false;
        }

        try {
            // For now, we'll log the order as a proxy
            // In a real implementation, this would create a formal PO in the WMS
            logger.info("Replenishment order recorded: OrderID=" + orderId + ", SKU=" + sku +
                       ", Qty=" + quantity + ", VendorID=" + vendorId + ", TargetDate=" + targetDate);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error recording replenishment order: " + orderId, e);
            return false;
        }
    }

    @Override
    public String getWarehouseStatus() {
        if (!connected) {
            return "Warehouse adapter not connected";
        }

        try {
            StringBuilder status = new StringBuilder();
            status.append("=== Warehouse Status ===\n");
            status.append("Warehouse ID: ").append(parameters.getWarehouseId()).append("\n");
            status.append("Warehouse Name: ").append(parameters.getWarehouseName()).append("\n");
            status.append("Location: ").append(parameters.getLocation()).append("\n");
            status.append("Operational: ").append(parameters.isOperational()).append("\n");
            status.append("Utilization: ").append(String.format("%.2f", parameters.getUtilizationPercentage()))
                    .append("%\n");
            status.append("Current Utilization: ").append(parameters.getCurrentUtilization())
                    .append(" / ").append(parameters.getTotalCapacity()).append("\n");
            status.append("Active Tasks: ").append(parameters.getActiveTaskCount()).append("\n");
            status.append("Avg Picking Time: ").append(parameters.getAveragePickingTimeMinutes())
                    .append(" minutes\n");
            status.append("Near Capacity: ").append(parameters.isNearCapacity()).append("\n");
            return status.toString();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting warehouse status", e);
            return "Error retrieving warehouse status: " + e.getMessage();
        }
    }

    @Override
    public boolean hasPendingAlerts() {
        // Placeholder for future alert management
        logger.fine("Checking for pending warehouse alerts");
        return false;
    }

    @Override
    public void close() {
        logger.info("Closing WarehouseManagementAdapter");
        connected = false;
        warehouseFacade = null;
    }

    /**
     * Helper method to retrieve the InventoryManager from the facade.
     *
     * @return The InventoryManager object or null if unavailable
     */
    private Object getInventoryManager() {
        if (!connected) {
            return null;
        }

        try {
            java.lang.reflect.Method getMethod = warehouseFacade.getClass()
                    .getMethod("getInventoryManager");
            return getMethod.invoke(warehouseFacade);
        } catch (Exception e) {
            logger.log(Level.FINE, "Error retrieving InventoryManager", e);
            return null;
        }
    }

    /**
     * Helper method to create a WMS Order object.
     *
     * @param orderId The order identifier
     * @param lineItems Map of SKU to quantity
     * @return A wms.models.Order object or null if creation fails
     */
    private Object createWMSOrder(String orderId, Map<String, Integer> lineItems) {
        try {
            Class<?> orderClass = Class.forName("wms.models.Order");
            Object order = orderClass.getConstructor(String.class).newInstance(orderId);

            java.lang.reflect.Method addItemMethod = orderClass.getMethod("addItem", String.class, int.class);
            for (Map.Entry<String, Integer> item : lineItems.entrySet()) {
                addItemMethod.invoke(order, item.getKey(), item.getValue());
            }

            return order;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error creating WMS Order", e);
            return null;
        }
    }

    /**
     * Helper method to create a WMS Product object.
     *
     * @param sku The product SKU
     * @param name The product name
     * @param category The product category
     * @return A wms.models.Product object or null if creation fails
     */
    private Object createWMSProduct(String sku, String name, String category) {
        try {
            Class<?> productClass = Class.forName("wms.models.Product");
            Class<?> categoryClass = Class.forName("wms.models.ProductCategory");

            // Get the category enum value
            Object categoryEnum = Enum.valueOf((Class<? extends Enum>) categoryClass, category.toUpperCase());

            // Create Product with SKU, name, and category
            Object product = productClass.getConstructor(String.class, String.class, categoryClass)
                    .newInstance(sku, name, categoryEnum);

            return product;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error creating WMS Product for SKU: " + sku, e);
            return null;
        }
    }

    /**
     * Helper method to get a default picking strategy.
     *
     * @return A picking strategy object or null
     */
    private Object getDefaultPickingStrategy() {
        try {
            // Try to use a standard FIFO strategy
            Class<?> strategyClass = Class.forName("wms.strategies.StandardFIFOStrategy");
            return strategyClass.getConstructor().newInstance();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not instantiate default picking strategy", e);
            return null;
        }
    }
}
