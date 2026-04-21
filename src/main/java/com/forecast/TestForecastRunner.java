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
import com.jackfruit.scm.database.model.DemandForecastingModels.SalesRecord;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TestForecastRunner {

    public static void main(String[] args) throws Exception {

        String productId = "P1001";
        String storeId = "S001";

        try (SupplyChainDatabaseFacade facade = new SupplyChainDatabaseFacade()) {
            DemandForecastingDbAdapter dbAdapter =
                    new DemandForecastingDbAdapter(facade);

            FeatureTimeSeries features = loadFromDatabase(dbAdapter, productId, storeId);

            LifeCycleManager lifeCycleManager = new LifeCycleManager();

            MLAlgorithmicExceptionSource exceptionSource =
                    new MLAlgorithmicExceptionSource();

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
    }

    private static FeatureTimeSeries loadFromDatabase(
            DemandForecastingDbAdapter dbAdapter,
            String productId,
            String storeId
    ) {
        List<SalesRecord> salesRecords = new ArrayList<>();
        for (SalesRecord record : dbAdapter.listSalesRecords()) {
            if (productId.equals(record.productId()) && storeId.equals(record.storeId())) {
                salesRecords.add(record);
            }
        }

        if (salesRecords.isEmpty()) {
            seedSampleSalesData(dbAdapter, productId, storeId);
            for (SalesRecord record : dbAdapter.listSalesRecords()) {
                if (productId.equals(record.productId()) && storeId.equals(record.storeId())) {
                    salesRecords.add(record);
                }
            }
        }

        salesRecords.sort(Comparator.comparing(SalesRecord::saleDate));

        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();

        for (SalesRecord record : salesRecords) {
            dates.add(record.saleDate());
            values.add(BigDecimal.valueOf(record.quantitySold()));
        }

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

    private static void seedSampleSalesData(
            DemandForecastingDbAdapter dbAdapter,
            String productId,
            String storeId
    ) {
        LocalDate startDate = LocalDate.now().minusMonths(35).withDayOfMonth(1);
        BigDecimal unitPrice = new BigDecimal("10.00");

        for (int i = 0; i < 36; i++) {
            int seasonalLift = (i % 12 == 10 || i % 12 == 11) ? 35 : 0;
            int quantitySold = 100 + (i * 2) + seasonalLift;
            BigDecimal revenue = unitPrice.multiply(BigDecimal.valueOf(quantitySold));

            dbAdapter.createSalesRecord(
                    new SalesRecord(
                            "SALE-SEED-" + productId + "-" + storeId + "-" + (i + 1),
                            productId,
                            storeId,
                            startDate.plusMonths(i),
                            quantitySold,
                            unitPrice,
                            revenue,
                            "NORTH"
                    )
            );
        }
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
