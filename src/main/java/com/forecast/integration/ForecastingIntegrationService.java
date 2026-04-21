package com.forecast.integration;

import com.forecast.models.FeatureTimeSeries;
import com.forecast.models.ForecastResult;
import com.forecast.models.LifecycleContent;
import com.forecast.models.PatternProfile;
import com.forecast.models.PromoData;
import com.forecast.models.RawSalesData;
import com.forecast.models.exceptions.ErrorCode;
import com.forecast.models.exceptions.ForecastingException;
import com.forecast.models.exceptions.IMLAlgorithmicExceptionSource;
import com.forecast.services.engine.lifecycle.LifeCycleManager;
import com.forecast.services.engine.strategy.ArimaHoltWintersStrategy;
import com.forecast.services.engine.strategy.ForecastStrategy;
import com.forecast.services.engine.strategy.ProphetLSTMStrategy;
import com.forecast.services.ingestion.feature.FeatureEngineeringService;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Public in-memory integration API for other subsystems.
 *
 * This service does not open database connections and does not persist results.
 * Callers can pass raw sales data directly, receive engineered features, and
 * generate forecasts. Database persistence remains available separately through
 * ForecastPersistenceService when needed.
 */
public class ForecastingIntegrationService {

    private final FeatureEngineeringService featureEngineeringService;
    private final LifeCycleManager lifeCycleManager;

    public ForecastingIntegrationService(IMLAlgorithmicExceptionSource exceptionSource) {
        this(new FeatureEngineeringService(exceptionSource), new LifeCycleManager());
    }

    public ForecastingIntegrationService(
        FeatureEngineeringService featureEngineeringService,
        LifeCycleManager lifeCycleManager
    ) {
        this.featureEngineeringService = Objects.requireNonNull(
            featureEngineeringService,
            "featureEngineeringService must not be null"
        );
        this.lifeCycleManager = Objects.requireNonNull(
            lifeCycleManager,
            "lifeCycleManager must not be null"
        );
    }

    public FeatureTimeSeries buildFeatures(
        String productId,
        String storeId,
        List<RawSalesData> rawRecords,
        List<PromoData> promoData,
        List<LocalDate> holidayDates
    ) {
        return featureEngineeringService.buildFeatures(
            productId,
            storeId,
            rawRecords,
            promoData,
            holidayDates
        );
    }

    public PatternProfile detectPatterns(FeatureTimeSeries features) {
        return featureEngineeringService.detectPatterns(features);
    }

    public ForecastResult generateForecast(
        String productId,
        String storeId,
        FeatureTimeSeries features,
        LifecycleContent lifecycle
    ) {
        Objects.requireNonNull(features, "features must not be null");

        LifecycleContent resolvedLifecycle = lifecycle != null
            ? lifecycle
            : lifeCycleManager.determineLifecycleStage(productId, storeId);

        if (!lifeCycleManager.isActiveStage(resolvedLifecycle)) {
            throw new ForecastingException(
                ErrorCode.FORECAST_MODEL_FAILURE,
                "Cannot forecast inactive lifecycle stage for product=" + productId
            );
        }

        PatternProfile pattern = featureEngineeringService.detectPatterns(features);
        String strategyName = lifeCycleManager.recommendForecastingStrategy(
            resolvedLifecycle,
            pattern
        );
        ForecastStrategy strategy = selectStrategy(strategyName);
        int horizon = Math.min(
            lifeCycleManager.getForecastHorizon(resolvedLifecycle),
            strategy.getMaximumForecastHorizon()
        );

        if (!strategy.validateInputData(features) || !strategy.train(features)) {
            throw new ForecastingException(
                ErrorCode.FORECAST_MODEL_FAILURE,
                "Strategy rejected input: " + strategy.getStrategyName()
            );
        }

        ForecastResult result = strategy.generateForecast(features, horizon);
        if (result == null) {
            throw new ForecastingException(
                ErrorCode.FORECAST_MODEL_FAILURE,
                "Strategy returned null result: " + strategy.getStrategyName()
            );
        }

        result.setProductId(productId);
        result.setStoreId(storeId);
        result.setLifecycleStage(resolvedLifecycle.getCurrentStage());
        return result;
    }

    private ForecastStrategy selectStrategy(String strategyName) {
        if (strategyName != null && strategyName.toUpperCase().contains("PROPHET")) {
            return new ProphetLSTMStrategy();
        }
        return new ArimaHoltWintersStrategy();
    }
}
