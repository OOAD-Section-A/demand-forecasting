package com.forecast.services.engine.strategy;

import com.forecast.models.FeatureTimeSeries;
import com.forecast.models.ForecastResult;
import java.util.List;

/**
 * ForecastStrategy defines the contract for different demand forecasting
 * algorithms and approaches. Implementations of this interface represent
 * different forecasting models (ARIMA, Prophet, LSTM, etc.) that can be
 * used interchangeably based on product lifecycle stage and data characteristics.
 *
 * Responsibilities:
 * - Generate demand forecasts using specific algorithms
 * - Validate input data compatibility
 * - Calculate accuracy metrics (MAPE, RMSE)
 * - Provide confidence intervals
 * - Handle model-specific parameters and configurations
 *
 * Supported Strategies:
 * - ARIMA: AutoRegressive Integrated Moving Average for trend/seasonal data
 * - PROPHET: Facebook's Prophet for business time series with strong seasonality
 * - LSTM: Long Short-Term Memory neural networks for complex patterns
 * - HOLT_WINTERS: Exponential smoothing for seasonal patterns
 *
 * @author Demand Forecasting Team
 * @version 1.0
 */
public interface ForecastStrategy {

    /**
     * Generates a forecast using this strategy's algorithm.
     *
     * @param features the engineered feature time series data
     * @param horizonMonths the forecast horizon in months
     * @return ForecastResult containing predictions and metrics
     */
    ForecastResult generateForecast(
        FeatureTimeSeries features,
        int horizonMonths
    );

    /**
     * Validates whether the input data is suitable for this strategy.
     *
     * @param features the feature time series to validate
     * @return true if data meets strategy requirements, false otherwise
     */
    boolean validateInputData(FeatureTimeSeries features);

    /**
     * Trains or fits the forecasting model with historical data.
     *
     * @param features the training feature time series
     * @return true if training was successful, false otherwise
     */
    boolean train(FeatureTimeSeries features);

    /**
     * Gets the minimum required data points for this strategy.
     *
     * @return minimum number of observations required
     */
    int getMinimumDataPoints();

    /**
     * Gets the maximum reliable forecast horizon for this strategy.
     *
     * @return maximum horizon in months
     */
    int getMaximumForecastHorizon();

    /**
     * Gets the name or identifier of this forecasting strategy.
     *
     * @return strategy name (e.g., "ARIMA", "PROPHET", "LSTM")
     */
    String getStrategyName();

    /**
     * Gets a description of this forecasting strategy.
     *
     * @return human-readable description of the strategy
     */
    String getDescription();

    /**
     * Calculates the Mean Absolute Percentage Error (MAPE) for validation.
     *
     * @param actual list of actual values
     * @param predicted list of predicted values
     * @return MAPE as a percentage
     */
    double calculateMape(List<Double> actual, List<Double> predicted);

    /**
     * Calculates the Root Mean Square Error (RMSE) for validation.
     *
     * @param actual list of actual values
     * @param predicted list of predicted values
     * @return RMSE value
     */
    double calculateRmse(List<Double> actual, List<Double> predicted);
}