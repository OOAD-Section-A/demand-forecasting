package com.forecast.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * PatternProfile represents detected patterns and characteristics in demand
 * time series data. It captures seasonal patterns, trends, cycles, and
 * anomalies that are identified during the feature engineering phase.
 *
 * Responsibilities:
 * - Store detected patterns in demand data
 * - Maintain pattern metadata and statistics
 * - Track pattern significance and confidence levels
 * - Provide access to pattern-specific forecasting insights
 *
 * Detected Patterns:
 * - SEASONAL: Repeating patterns within a year
 * - TREND: Long-term upward or downward movement
 * - CYCLIC: Patterns with periods > 1 year
 * - RANDOM: Unexplained variance
 * - ANOMALY: Unusual data points or periods
 *
 * @author Demand Forecasting Team
 * @version 1.0
 */
public class PatternProfile {

    private String productId;
    private String storeId;
    private LocalDate analysisDate;
    private String dominantPattern;
    private Double seasonalityStrength;
    private Integer seasonalPeriodDays;
    private Double trendStrength;
    private String trendDirection; // UP, DOWN, FLAT
    private Double cyclicityStrength;
    private Integer cyclicPeriodDays;
    private Double noiseLevel;
    private List<String> detectedPatterns;
    private Map<String, Double> patternConfidences;
    private List<LocalDate> anomalyDates;
    private Double patternStability;
    private String forecastingRecommendation;
    private Boolean requiresSpecialHandling;

    /**
     * Default constructor for PatternProfile.
     */
    public PatternProfile() {
        this.analysisDate = LocalDate.now();
        this.requiresSpecialHandling = false;
    }

    /**
     * Constructor with essential pattern parameters.
     *
     * @param productId the product identifier
     * @param storeId the store identifier
     * @param dominantPattern the primary pattern detected
     */
    public PatternProfile(
        String productId,
        String storeId,
        String dominantPattern
    ) {
        this.productId = productId;
        this.storeId = storeId;
        this.dominantPattern = dominantPattern;
        this.analysisDate = LocalDate.now();
        this.requiresSpecialHandling = false;
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

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public void setAnalysisDate(LocalDate analysisDate) {
        this.analysisDate = analysisDate;
    }

    public String getDominantPattern() {
        return dominantPattern;
    }

    public void setDominantPattern(String dominantPattern) {
        this.dominantPattern = dominantPattern;
    }

    public Double getSeasonalityStrength() {
        return seasonalityStrength;
    }

    public void setSeasonalityStrength(Double seasonalityStrength) {
        this.seasonalityStrength = seasonalityStrength;
    }

    public Integer getSeasonalPeriodDays() {
        return seasonalPeriodDays;
    }

    public void setSeasonalPeriodDays(Integer seasonalPeriodDays) {
        this.seasonalPeriodDays = seasonalPeriodDays;
    }

    public Double getTrendStrength() {
        return trendStrength;
    }

    public void setTrendStrength(Double trendStrength) {
        this.trendStrength = trendStrength;
    }

    public String getTrendDirection() {
        return trendDirection;
    }

    public void setTrendDirection(String trendDirection) {
        this.trendDirection = trendDirection;
    }

    public Double getCyclicityStrength() {
        return cyclicityStrength;
    }

    public void setCyclicityStrength(Double cyclicityStrength) {
        this.cyclicityStrength = cyclicityStrength;
    }

    public Integer getCyclicPeriodDays() {
        return cyclicPeriodDays;
    }

    public void setCyclicPeriodDays(Integer cyclicPeriodDays) {
        this.cyclicPeriodDays = cyclicPeriodDays;
    }

    public Double getNoiseLevel() {
        return noiseLevel;
    }

    public void setNoiseLevel(Double noiseLevel) {
        this.noiseLevel = noiseLevel;
    }

    public List<String> getDetectedPatterns() {
        return detectedPatterns;
    }

    public void setDetectedPatterns(List<String> detectedPatterns) {
        this.detectedPatterns = detectedPatterns;
    }

    public Map<String, Double> getPatternConfidences() {
        return patternConfidences;
    }

    public void setPatternConfidences(Map<String, Double> patternConfidences) {
        this.patternConfidences = patternConfidences;
    }

    public List<LocalDate> getAnomalyDates() {
        return anomalyDates;
    }

    public void setAnomalyDates(List<LocalDate> anomalyDates) {
        this.anomalyDates = anomalyDates;
    }

    public Double getPatternStability() {
        return patternStability;
    }

    public void setPatternStability(Double patternStability) {
        this.patternStability = patternStability;
    }

    public String getForecastingRecommendation() {
        return forecastingRecommendation;
    }

    public void setForecastingRecommendation(String forecastingRecommendation) {
        this.forecastingRecommendation = forecastingRecommendation;
    }

    public Boolean getRequiresSpecialHandling() {
        return requiresSpecialHandling;
    }

    public void setRequiresSpecialHandling(Boolean requiresSpecialHandling) {
        this.requiresSpecialHandling = requiresSpecialHandling;
    }

    /**
     * Determines if the pattern profile indicates high volatility.
     *
     * @return true if noise level is high or pattern stability is low
     */
    public boolean isHighVolatility() {
        boolean highNoise = noiseLevel != null && noiseLevel > 0.5;
        boolean lowStability =
            patternStability != null && patternStability < 0.5;
        return highNoise || lowStability;
    }
}
