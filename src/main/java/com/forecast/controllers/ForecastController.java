package com.forecast.controllers;

import com.forecast.models.FeatureTimeSeries;
import com.forecast.models.ForecastResult;
import com.forecast.models.LifecycleContent;
import com.forecast.services.engine.ForecastProcessor;

import java.util.logging.Logger;

/**
 * ForecastController handles all HTTP requests and operations related to
 * demand forecasting functionality.
 */
public class ForecastController {

    private static final Logger logger = Logger.getLogger(ForecastController.class.getName());

    private final ForecastProcessor forecastProcessor;

    /**
     * Constructor with dependency injection
     */
    public ForecastController(ForecastProcessor forecastProcessor) {
        this.forecastProcessor = forecastProcessor;
        logger.info("ForecastController initialized");
    }

    /**
     * Generates a forecast for a given product.
     *
     * @param productId product identifier
     * @param storeId store identifier
     * @param features engineered feature time-series input
     * @param lifecycle lifecycle context; may be null if processor should resolve it
     * @return ForecastResult
     */
    public ForecastResult generateForecast(
        String productId,
        String storeId,
        FeatureTimeSeries features,
        LifecycleContent lifecycle
    ) {
        logger.info("Received forecast request for product [" + productId + "] at store [" + storeId + "]");

        if (forecastProcessor == null) {
            throw new IllegalStateException("ForecastProcessor is not configured");
        }

        return forecastProcessor.processForecast(productId, storeId, features, lifecycle);
    }
}