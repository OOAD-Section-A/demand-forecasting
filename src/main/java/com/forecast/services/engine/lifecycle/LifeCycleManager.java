package com.forecast.services.engine.lifecycle;

import com.forecast.models.LifecycleContent;
import com.forecast.models.PatternProfile;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

public class LifeCycleManager {

    private static final Logger logger = Logger.getLogger(LifeCycleManager.class.getName());

    public LifeCycleManager() {
        logger.fine("LifeCycleManager initialized");
    }

    public LifecycleContent determineLifecycleStage(String productId, String storeId) {
        logger.info("Determining lifecycle stage for product [" + productId + "] at store [" + storeId + "]");

        LifecycleContent lifecycle = new LifecycleContent(
            productId,
            "MATURITY",
            LocalDate.now().minusMonths(12)
        );
        lifecycle.setDescription("Defaulted to MATURITY until lifecycle registry integration is connected.");
        lifecycle.setExpectedGrowthRate(0.02);
        lifecycle.setExpectedDemandVariability(0.20);
        lifecycle.setForecastHorizonMonths(6);
        lifecycle.setUseSeasonalAdjustment(true);
        lifecycle.setNotes("Heuristic default stage.");
        lifecycle.setLastUpdated(LocalDate.now());
        return lifecycle;
    }

    public boolean updateLifecycleStage(LifecycleContent lifecycle) {
        if (lifecycle == null) {
            return false;
        }
        lifecycle.setLastUpdated(LocalDate.now());
        logger.info(
            "Lifecycle stage updated for product [" +
            lifecycle.getProductId() +
            "] -> [" +
            lifecycle.getCurrentStage() +
            "]"
        );
        return true;
    }

    public int getForecastHorizon(LifecycleContent lifecycle) {
        if (lifecycle == null) {
            logger.warning("Lifecycle is null, defaulting forecast horizon to 6 months");
            return 6;
        }

        logger.fine("Determining forecast horizon for stage [" + lifecycle.getCurrentStage() + "]");

        if (lifecycle.getForecastHorizonMonths() != null && lifecycle.getForecastHorizonMonths() > 0) {
            return lifecycle.getForecastHorizonMonths();
        }

        String stage = safeUpper(lifecycle.getCurrentStage());
        switch (stage) {
            case "INTRODUCTION":
                return 3;
            case "GROWTH":
                return 6;
            case "DECLINE":
                return 3;
            case "DISCONTINUED":
                return 1;
            case "MATURITY":
            default:
                return 6;
        }
    }

    public String recommendForecastingStrategy(LifecycleContent lifecycle, PatternProfile pattern) {
        String stage = lifecycle == null ? "" : safeUpper(lifecycle.getCurrentStage());
        logger.info("Recommending forecasting strategy for stage [" + stage + "]");

        String dominantPattern = pattern != null ? safeUpper(pattern.getDominantPattern()) : "";

        if ("INTRODUCTION".equals(stage) || "GROWTH".equals(stage)) {
            return "PROPHET_LSTM";
        }

        if ("DECLINE".equals(stage) || "DISCONTINUED".equals(stage)) {
            return "ARIMA_HOLT_WINTERS";
        }

        if (pattern != null) {
            if (pattern.isHighVolatility() ||
                List.of("PROMOTIONAL", "ANOMALY", "MULTI_SEASONAL").contains(dominantPattern)) {
                return "PROPHET_LSTM";
            }

            if (List.of("SEASONAL", "TREND", "STABLE").contains(dominantPattern)) {
                return "ARIMA_HOLT_WINTERS";
            }
        }

        return "ARIMA_HOLT_WINTERS";
    }

    public boolean isActiveStage(LifecycleContent lifecycle) {
        if (lifecycle == null) {
            return false;
        }

        String stage = safeUpper(lifecycle.getCurrentStage());
        logger.fine("Checking if stage [" + stage + "] is active");

        return !("DISCONTINUED".equals(stage) || "INACTIVE".equals(stage));
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}