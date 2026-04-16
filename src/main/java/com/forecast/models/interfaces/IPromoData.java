package com.forecast.models.interfaces;

import com.forecast.models.PromoData;
import java.time.LocalDate;
import java.util.List;

/**
 * IPromoData defines the contract for managing promotional data and its
 * impact on demand forecasting. Implementations of this interface handle
 * promotional campaign information, discount structures, and demand lift metrics.
 *
 * Responsibilities:
 * - Retrieve promotional campaigns for products and stores
 * - Calculate promotional impact on demand
 * - Track promotion effectiveness metrics
 * - Manage active and historical promotional data
 * - Integrate promotional features into forecasting models
 *
 * @author Demand Forecasting Team
 * @version 1.0
 */
public interface IPromoData {
    /**
     * Retrieves all active promotions for a specific product and store.
     *
     * @param productId the product identifier
     * @param storeId the store identifier
     * @return list of active promotional data for the product-store combination
     */
    List<PromoData> getActivePromotions(String productId, String storeId);

    /**
     * Retrieves promotions within a specific date range.
     *
     * @param productId the product identifier
     * @param storeId the store identifier
     * @param startDate the start of the date range
     * @param endDate the end of the date range
     * @return list of promotions active within the specified period
     */
    List<PromoData> getPromotionsByDateRange(
        String productId,
        String storeId,
        LocalDate startDate,
        LocalDate endDate
    );

    /**
     * Calculates the aggregate demand lift from all active promotions.
     *
     * @param productId the product identifier
     * @param storeId the store identifier
     * @return estimated demand lift percentage, or 0 if no active promotions
     */
    double calculateAggregateDemandLift(String productId, String storeId);

    /**
     * Checks if a product has any active promotions.
     *
     * @param productId the product identifier
     * @param storeId the store identifier
     * @return true if active promotions exist, false otherwise
     */
    boolean hasActivePromotions(String productId, String storeId);

    /**
     * Saves or updates promotional data.
     *
     * @param promoData the promotional data to save
     * @return true if operation was successful, false otherwise
     */
    boolean savePromotionData(PromoData promoData);

    /**
     * Deletes a promotion record.
     *
     * @param promotionId the unique promotion identifier
     * @return true if deletion was successful, false otherwise
     */
    boolean deletePromotion(String promotionId);
}
