package com.forecast.models.interfaces;

import com.forecast.models.RawSalesData;
import java.util.List;

/**
 * Contract for all data source connectors.
 * DataSourceConnector (and its DB / CSV variants) implement this.
 * Keeping this as an abstraction means ForecastProcessor and
 * FeatureEngineeringService never touch a concrete connector class —
 * satisfying DIP.
 */
public interface IDataConnector {

    /**
     * Opens the underlying connection (DB pool checkout or file handle).
     * Must be called before fetch().
     *
     * @throws com.forecast.exception.ForecastingException with
     *         ErrorCode.DATA_SOURCE_UNAVAILABLE if the source is unreachable.
     */
    void connect();

    /**
     * Fetches all available sales records from the source.
     *
     * @return unmodifiable list of raw sales records; never null, may be empty.
     */
    List<RawSalesData> fetchSalesData();

    /**
     * Releases the underlying connection back to the pool (DB) or
     * closes the file handle (CSV). Safe to call multiple times.
     */
    void disconnect();

    /**
     * Returns true if the connector currently holds an open connection.
     */
    boolean isConnected();
}