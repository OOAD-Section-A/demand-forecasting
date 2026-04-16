package com.forecast.controllers;

import com.forecast.models.RawSalesData;
import com.forecast.services.ingestion.connector.DataSourceConnector;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IngestionController handles all HTTP requests and operations related to
 * data ingestion functionality. It orchestrates the interaction between
 * the presentation layer and the data ingestion service layer.
 *
 * Responsibilities:
 * - Handle incoming data ingestion requests from clients
 * - Coordinate with DataSourceConnector implementations
 * - Manage data source connections and disconnections
 * - Return ingestion status and results
 * - Handle error responses and validation
 *
 * @author Demand Forecasting Team
 * @version 1.0
 */
public class IngestionController {

    private static final Logger logger = LoggerFactory.getLogger(
        IngestionController.class
    );

    private final DataSourceConnector dataSourceConnector;

    /**
     * Constructor with dependency injection.
     *
     * @param dataSourceConnector connector used to fetch source data
     */
    public IngestionController(DataSourceConnector dataSourceConnector) {
        this.dataSourceConnector = dataSourceConnector;
        logger.debug("IngestionController initialized");
    }

    /**
     * Initiates data ingestion from a configured data source.
     *
     * @return List of RawSalesData objects fetched from the source
     */
    public List<RawSalesData> ingestData() {
        logger.info("Starting data ingestion process...");

        if (dataSourceConnector == null) {
            logger.error("DataSourceConnector is not configured");
            throw new IllegalStateException(
                "Data source connector is not configured"
            );
        }

        try {
            dataSourceConnector.connect();

            if (!dataSourceConnector.isConnected()) {
                logger.error("Data source connection could not be established");
                throw new RuntimeException("Unable to connect to data source");
            }

            List<RawSalesData> rawSalesDataList =
                dataSourceConnector.fetchSalesData();

            if (rawSalesDataList == null || rawSalesDataList.isEmpty()) {
                logger.warn("No sales data returned from data source");
                return Collections.emptyList();
            }

            logger.info(
                "Data ingestion completed successfully. Records fetched: {}",
                rawSalesDataList.size()
            );

            return rawSalesDataList;
        } catch (Exception e) {
            logger.error("Error during data ingestion: {}", e.getMessage(), e);
            throw new RuntimeException("Data ingestion failed", e);
        } finally {
            try {
                dataSourceConnector.disconnect();
            } catch (Exception e) {
                logger.warn(
                    "Error while disconnecting data source: {}",
                    e.getMessage(),
                    e
                );
            }
        }
    }

    /**
     * Validates the data source connection.
     *
     * @return true if connection is successful, false otherwise
     */
    public boolean validateConnection() {
        logger.info("Validating data source connection...");

        if (dataSourceConnector == null) {
            logger.error("DataSourceConnector is not configured");
            return false;
        }

        try {
            dataSourceConnector.connect();
            boolean isValid = dataSourceConnector.isConnected();

            if (isValid) {
                logger.info("Data source connection validated successfully");
            } else {
                logger.warn("Data source connection validation failed");
            }

            return isValid;
        } catch (Exception e) {
            logger.error(
                "Error validating data source connection: {}",
                e.getMessage(),
                e
            );
            return false;
        } finally {
            try {
                dataSourceConnector.disconnect();
            } catch (Exception e) {
                logger.warn(
                    "Error while disconnecting data source: {}",
                    e.getMessage(),
                    e
                );
            }
        }
    }
}
