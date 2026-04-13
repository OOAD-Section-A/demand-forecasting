package com.forecast.models;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * PromoData represents promotional campaign information and its impact on
 * demand forecasting. It captures details about promotions, discounts, and
 * marketing campaigns that influence product demand.
 *
 * Responsibilities:
 * - Store promotional campaign details
 * - Maintain promotion timing and scope information
 * - Track promotional impact metrics
 * - Provide access to promotion characteristics for feature engineering
 *
 * Promotion Types:
 * - DISCOUNT: Price reduction or special pricing
 * - BOGO: Buy One Get One promotions
 * - BUNDLE: Multi-product bundled offers
 * - SEASONAL: Holiday or seasonal promotions
 * - CLEARANCE: End-of-season or inventory clearance
 * - LOYALTY: Customer loyalty program offers
 *
 * @author Demand Forecasting Team
 * @version 1.0
 */
public class PromoData {

    private String productId;
    private String storeId;
    private String promotionId;
    private String promotionType;
    private LocalDate promotionStartDate;
    private LocalDate promotionEndDate;
    private BigDecimal discountPercentage;
    private BigDecimal discountAmount;
    private String description;
    private Boolean isActive;
    private Double expectedDemandLift;
    private Double actualDemandLift;
    private Integer budgetAmount;
    private String channel; // INSTORE, ONLINE, BOTH
    private String targetAudience;
    private LocalDate analysisDate;

    /**
     * Default constructor for PromoData.
     */
    public PromoData() {
        this.isActive = false;
        this.analysisDate = LocalDate.now();
    }

    /**
     * Constructor with essential promotion parameters.
     *
     * @param productId the product identifier
     * @param storeId the store identifier
     * @param promotionId unique promotion identifier
     * @param promotionType the type of promotion
     */
    public PromoData(
        String productId,
        String storeId,
        String promotionId,
        String promotionType
    ) {
        this.productId = productId;
        this.storeId = storeId;
        this.promotionId = promotionId;
        this.promotionType = promotionType;
        this.isActive = false;
        this.analysisDate = LocalDate.now();
    }

    // Getters and Setters

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getPromotionId() {
        return promotionId;
    }

    public void setPromotionId(String promotionId) {
        this.promotionId = promotionId;
    }

    public String getPromotionType() {
        return promotionType;
    }

    public void setPromotionType(String promotionType) {
        this.promotionType = promotionType;
    }

    public LocalDate getPromotionStartDate() {
        return promotionStartDate;
    }

    public void setPromotionStartDate(LocalDate promotionStartDate) {
        this.promotionStartDate = promotionStartDate;
    }

    public LocalDate getPromotionEndDate() {
        return promotionEndDate;
    }

    public void setPromotionEndDate(LocalDate promotionEndDate) {
        this.promotionEndDate = promotionEndDate;
    }

    public BigDecimal getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(BigDecimal discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Double getExpectedDemandLift() {
        return expectedDemandLift;
    }

    public void setExpectedDemandLift(Double expectedDemandLift) {
        this.expectedDemandLift = expectedDemandLift;
    }

    public Double getActualDemandLift() {
        return actualDemandLift;
    }

    public void setActualDemandLift(Double actualDemandLift) {
        this.actualDemandLift = actualDemandLift;
    }

    public Integer getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(Integer budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTargetAudience() {
        return targetAudience;
    }

    public void setTargetAudience(String targetAudience) {
        this.targetAudience = targetAudience;
    }

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public void setAnalysisDate(LocalDate analysisDate) {
        this.analysisDate = analysisDate;
    }

    /**
     * Determines if the promotion is currently active based on dates.
     *
     * @return true if current date is within promotion period
     */
    public boolean isCurrentlyActive() {
        LocalDate today = LocalDate.now();
        return (
            promotionStartDate != null &&
            promotionEndDate != null &&
            !today.isBefore(promotionStartDate) &&
            !today.isAfter(promotionEndDate)
        );
    }

    /**
     * Calculates the ROI of the promotion if actual lift is available.
     *
     * @return return on investment percentage, or null if data unavailable
     */
    public Double calculateRoi() {
        if (
            actualDemandLift == null ||
            budgetAmount == null ||
            budgetAmount == 0
        ) {
            return null;
        }
        return (actualDemandLift / budgetAmount) * 100;
    }
}
