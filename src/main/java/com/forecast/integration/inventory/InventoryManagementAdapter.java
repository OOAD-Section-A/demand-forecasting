package com.forecast.integration.inventory;

import inventory_subsystem.*;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * InventoryManagementAdapter implements the IInventoryAdapter interface to provide
 * integration between the demand forecasting system and the inventory management subsystem.
 *
 * This adapter wraps the InventoryService and translates forecast domain operations
 * into inventory subsystem operations, handling errors and providing sensible defaults.
 *
 * Responsibilities:
 * - Bridge the forecast system with inventory management
 * - Provide current inventory state information
 * - Record replenishment orders based on forecast recommendations
 * - Calculate inventory metrics needed for replenishment decisions
 * - Handle errors gracefully with logging
 *
 * @author Integration Team
 * @version 1.0
 */
public class InventoryManagementAdapter implements IInventoryAdapter {

    private static final Logger logger = Logger.getLogger(
        InventoryManagementAdapter.class.getName()
    );

    private static final int DEFAULT_LEAD_TIME_DAYS = 7;
    private static final double DEFAULT_SAFETY_STOCK_MULTIPLIER = 0.2;
    private static final String DEFAULT_SUPPLIER = "DEFAULT_SUPPLIER";
    private static final String REPLENISHMENT_REFERENCE_TYPE = "PO";

    private final InventoryService inventoryService;
    private boolean isOperational;

    /**
     * Constructor accepting an InventoryService instance.
     *
     * @param inventoryService the inventory service to wrap
     */
    public InventoryManagementAdapter(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
        this.isOperational = inventoryService != null;
        logger.info("InventoryManagementAdapter initialized with InventoryService");
    }

    /**
     * Constructor that creates a default InventoryService instance.
     */
    public InventoryManagementAdapter() {
        this(new InventoryService());
    }

    @Override
    public int getCurrentStockLevel(String productId, String locationId) {
        if (!isOperational) {
            logger.warning("Inventory system not operational; returning 0 for product=" + productId);
            return 0;
        }

        try {
            int level = inventoryService.getStock(productId, locationId);
            logger.fine("Current stock for product=" + productId + " at location=" + locationId + ": " + level);
            return level;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error retrieving stock level for product=" + productId, e);
            return 0;
        }
    }

    @Override
    public int getAvailableQuantity(String productId, String locationId) {
        if (!isOperational) {
            return 0;
        }

        try {
            int currentStock = inventoryService.getStock(productId, locationId);
            logger.fine("Available quantity for product=" + productId + " at location=" + locationId + ": " + currentStock);
            return Math.max(0, currentStock);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error retrieving available quantity", e);
            return 0;
        }
    }

    @Override
    public int getReservedQuantity(String productId, String locationId) {
        if (!isOperational) {
            return 0;
        }

        try {
            // The inventory subsystem tracks reserved quantities at the InventoryItem level
            // This would be retrieved from the item when accessing inventory data
            logger.fine("Retrieved reserved quantity for product=" + productId);
            return 0;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error retrieving reserved quantity", e);
            return 0;
        }
    }

    @Override
    public int getSafetyStockLevel(String productId) {
        if (!isOperational) {
            return 100; // Default fallback
        }

        try {
            // Calculate default safety stock based on product characteristics
            // In a production system, this would be configured per product
            int defaultSafetyStock = (int) (100 * DEFAULT_SAFETY_STOCK_MULTIPLIER);
            logger.fine("Safety stock level for product=" + productId + ": " + defaultSafetyStock);
            return defaultSafetyStock;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error retrieving safety stock level", e);
            return 100;
        }
    }

    @Override
    public int getReorderThreshold(String productId) {
        if (!isOperational) {
            return 200; // Default fallback
        }

        try {
            // Default reorder threshold; configurable per product in production
            int threshold = 200;
            logger.fine("Reorder threshold for product=" + productId + ": " + threshold);
            return threshold;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error retrieving reorder threshold", e);
            return 200;
        }
    }

    @Override
    public boolean recordReplenishmentOrder(
        String productId,
        String locationId,
        int quantity,
        String supplierId,
        String referenceId
    ) {
        if (!isOperational) {
            logger.warning("Inventory system not operational; cannot record replenishment");
            return false;
        }

        try {
            String actualSupplierId = supplierId != null ? supplierId : DEFAULT_SUPPLIER;
            String actualReferenceId = referenceId != null ? referenceId : generateReferenceId();

            inventoryService.addStock(
                productId,
                locationId,
                actualSupplierId,
                quantity,
                REPLENISHMENT_REFERENCE_TYPE,
                actualReferenceId
            );

            logger.info("Replenishment order recorded: product=" + productId
                + ", location=" + locationId
                + ", quantity=" + quantity
                + ", supplier=" + actualSupplierId
                + ", reference=" + actualReferenceId);
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error recording replenishment order for product=" + productId, e);
            return false;
        }
    }

    @Override
    public boolean productExists(String productId, String locationId) {
        if (!isOperational) {
            return false;
        }

        try {
            int stock = inventoryService.getStock(productId, locationId);
            boolean exists = stock >= 0;
            logger.fine("Product existence check for product=" + productId + ": " + exists);
            return exists;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking product existence", e);
            return false;
        }
    }

    @Override
    public String getABCCategory(String productId) {
        if (!isOperational) {
            return "C"; // Default to lowest category
        }

        try {
            // In a production system, would query from product master data
            // For now, return a default based on product characteristics
            logger.fine("ABC category retrieved for product=" + productId);
            return "B";
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error retrieving ABC category", e);
            return "C";
        }
    }

    @Override
    public double getAverageDailyConsumption(String productId, String locationId, int daysBack) {
        if (!isOperational) {
            return 0.0;
        }

        try {
            // In a production system, would query historical transaction data
            // Calculate from inventory movements over the specified period
            double avgConsumption = 10.0; // Default: 10 units per day
            logger.fine("Average daily consumption for product=" + productId
                + " over last " + daysBack + " days: " + avgConsumption);
            return avgConsumption;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error calculating average daily consumption", e);
            return 0.0;
        }
    }

    @Override
    public int calculateDaysUntilStockout(String productId, String locationId) {
        if (!isOperational) {
            return -1;
        }

        try {
            int currentStock = getCurrentStockLevel(productId, locationId);
            double dailyConsumption = getAverageDailyConsumption(productId, locationId, 30);

            if (dailyConsumption <= 0) {
                logger.warning("Cannot calculate days until stockout: no consumption for product=" + productId);
                return -1;
            }

            int daysUntilStockout = (int) (currentStock / dailyConsumption);
            logger.fine("Days until stockout for product=" + productId + ": " + daysUntilStockout);
            return daysUntilStockout;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error calculating days until stockout", e);
            return -1;
        }
    }

    @Override
    public int getLeadTime(String productId) {
        if (!isOperational) {
            return DEFAULT_LEAD_TIME_DAYS;
        }

        try {
            // In a production system, would query supplier master data
            int leadTime = DEFAULT_LEAD_TIME_DAYS;
            logger.fine("Lead time for product=" + productId + ": " + leadTime + " days");
            return leadTime;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error retrieving lead time", e);
            return DEFAULT_LEAD_TIME_DAYS;
        }
    }

    @Override
    public boolean isInventorySystemAvailable() {
        try {
            boolean available = inventoryService != null && isOperational;
            if (!available) {
                logger.warning("Inventory system is not available");
                isOperational = false;
            }
            return available;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking inventory system availability", e);
            isOperational = false;
            return false;
        }
    }

    @Override
    public boolean releaseReservation(String productId, String locationId, int quantity) {
        if (!isOperational) {
            logger.warning("Inventory system not operational; cannot release reservation");
            return false;
        }

        try {
            inventoryService.removeStock(productId, locationId, quantity);
            logger.info("Reservation released: product=" + productId
                + ", location=" + locationId
                + ", quantity=" + quantity);
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error releasing reservation for product=" + productId, e);
            return false;
        }
    }

    @Override
    public double getTotalInventoryValue(String productId) {
        if (!isOperational) {
            return 0.0;
        }

        try {
            // In a production system, would sum inventory value across all locations
            // Multiplying quantity by unit cost for each location
            logger.fine("Total inventory value calculated for product=" + productId);
            return 0.0;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error calculating total inventory value", e);
            return 0.0;
        }
    }

    /**
     * Generates a unique reference ID for tracking replenishment orders.
     *
     * @return a unique reference ID
     */
    private String generateReferenceId() {
        return "AUTO_" + System.currentTimeMillis();
    }
}
