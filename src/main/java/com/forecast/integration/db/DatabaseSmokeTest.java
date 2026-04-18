package com.forecast.integration.db;

import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.DemandForecastingModels.SalesRecord;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DatabaseSmokeTest {

    public static void main(String[] args) {
        SupplyChainDatabaseFacade facade = new SupplyChainDatabaseFacade();
        DemandForecastingDbAdapter adapter = new DemandForecastingDbAdapter(facade);

        SalesRecord record = new SalesRecord(
                "SALE-1001",
                "P1001",
                "S001",
                LocalDate.now(),
                10,
                new BigDecimal("99.99"),
                new BigDecimal("999.90"),
                "South"
        );

        adapter.createSalesRecord(record);

        System.out.println("Inserted sales record.");
        System.out.println("All sales records:");
        adapter.getAllSalesRecords().forEach(System.out::println);

        adapter.close();
    }
}