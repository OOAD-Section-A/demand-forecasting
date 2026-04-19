package com.forecast;

import com.forecast.controllers.ForecastController;
import com.forecast.integration.db.DemandForecastingDbAdapter;
import com.forecast.integration.db.ForecastPersistenceService;
import com.forecast.models.FeatureTimeSeries;
import com.forecast.models.ForecastResult;
import com.forecast.models.LifecycleContent;
import com.forecast.models.exceptions.MLAlgorithmicExceptionSource;
import com.forecast.services.engine.ForecastProcessor;
import com.forecast.services.engine.lifecycle.LifeCycleManager;
import com.forecast.services.output.ForecastOutputService;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;


import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TestForecastRunner {

    public static void main(String[] args) throws Exception {

        String productId = "P1001";
        String storeId = "S001";

        FeatureTimeSeries features = loadFromDatabase(productId, storeId);

        LifeCycleManager lifeCycleManager = new LifeCycleManager();

        MLAlgorithmicExceptionSource exceptionSource =
                new MLAlgorithmicExceptionSource();

        SupplyChainDatabaseFacade facade = new SupplyChainDatabaseFacade();
        DemandForecastingDbAdapter dbAdapter =
                new DemandForecastingDbAdapter(facade);
        ForecastPersistenceService persistenceService =
                new ForecastPersistenceService(dbAdapter);

        ForecastOutputService outputService =
                new ForecastOutputService(exceptionSource, persistenceService);

        ForecastProcessor processor =
                new ForecastProcessor(lifeCycleManager, outputService, exceptionSource);

        ForecastController controller =
                new ForecastController(processor);

        LifecycleContent lifecycle = null;

        ForecastResult result = controller.generateForecast(
                productId,
                storeId,
                features,
                lifecycle
        );

        printResult(result);
    }

    private static FeatureTimeSeries loadFromDatabase(String productId, String storeId) throws Exception {

        Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/OOAD",
                "root",
                "anurag10"
        );

        String query =
                "SELECT sale_date, quantity_sold " +
                "FROM sales_records " +
                "WHERE product_id = ? AND store_id = ? " +
                "ORDER BY sale_date";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, productId);
        stmt.setString(2, storeId);

        ResultSet rs = stmt.executeQuery();

        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();

        while (rs.next()) {
            dates.add(rs.getDate("sale_date").toLocalDate());
            values.add(BigDecimal.valueOf(rs.getInt("quantity_sold")));
        }

        rs.close();
        stmt.close();
        conn.close();

        if (dates.isEmpty()) {
            throw new RuntimeException(
                    "No sales data found for product=" + productId + ", store=" + storeId
            );
        }

        FeatureTimeSeries features =
                new FeatureTimeSeries(productId, storeId, dates, values);

        features.setNormalized(false);

        return features;
    }

    private static void printResult(ForecastResult result) {
        if (result == null) {
            System.out.println("No forecast result returned.");
            return;
        }

        System.out.println("===== FORECAST RESULT =====");
        System.out.println("Product ID          : " + result.getProductId());
        System.out.println("Store ID            : " + result.getStoreId());
        System.out.println("Lifecycle Stage     : " + result.getLifecycleStage());
        System.out.println("Generated Date      : " + result.getForecastGeneratedDate());
        System.out.println("Forecast Start Date : " + result.getForecastStartDate());
        System.out.println("Forecast End Date   : " + result.getForecastEndDate());
        System.out.println("Model Used          : " + result.getModelUsed());
        System.out.println("Status              : " + result.getStatus());
        System.out.println("MAPE                : " + result.getMape());
        System.out.println("RMSE                : " + result.getRmse());

        System.out.println("\nForecasted Demand:");
        if (result.getForecastedDemand() != null) {
            for (int i = 0; i < result.getForecastedDemand().size(); i++) {
                String lower = result.getConfidenceIntervalLower() != null
                        && i < result.getConfidenceIntervalLower().size()
                        ? result.getConfidenceIntervalLower().get(i).toString()
                        : "-";

                String upper = result.getConfidenceIntervalUpper() != null
                        && i < result.getConfidenceIntervalUpper().size()
                        ? result.getConfidenceIntervalUpper().get(i).toString()
                        : "-";

                System.out.println(
                        "Month " + (i + 1) +
                        " -> Forecast=" + result.getForecastedDemand().get(i) +
                        ", Lower=" + lower +
                        ", Upper=" + upper
                );
            }
        }
    }
}