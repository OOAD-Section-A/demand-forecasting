package com.forecast.integration.db;

import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.DemandForecast;
import com.jackfruit.scm.database.model.DemandForecastingModels.ForecastPerformanceMetric;
import com.jackfruit.scm.database.model.DemandForecastingModels.HolidayCalendar;
import com.jackfruit.scm.database.model.DemandForecastingModels.InventorySupply;
import com.jackfruit.scm.database.model.DemandForecastingModels.ProductLifecycleStage;
import com.jackfruit.scm.database.model.DemandForecastingModels.ProductMetadata;
import com.jackfruit.scm.database.model.DemandForecastingModels.PromotionalCalendar;
import com.jackfruit.scm.database.model.DemandForecastingModels.SalesRecord;

import java.util.List;

public class DemandForecastingDbAdapter {

    private final SupplyChainDatabaseFacade facade;

    public DemandForecastingDbAdapter(SupplyChainDatabaseFacade facade) {
        this.facade = facade;
    }

    public void createForecast(DemandForecast forecast) {
        facade.demandForecasting().createForecast(forecast);
    }

    public List<DemandForecast> getAllForecasts() {
        return facade.demandForecasting().listForecasts();
    }

    public void createSalesRecord(SalesRecord salesRecord) {
        facade.demandForecasting().createSalesRecord(salesRecord);
    }

    public List<SalesRecord> getAllSalesRecords() {
        return facade.demandForecasting().listSalesRecords();
    }

    public void createHolidayCalendar(HolidayCalendar holidayCalendar) {
        facade.demandForecasting().createHolidayCalendar(holidayCalendar);
    }

    public void createPromotionalCalendar(PromotionalCalendar promotionalCalendar) {
        facade.demandForecasting().createPromotionalCalendar(promotionalCalendar);
    }

    public void createProductMetadata(ProductMetadata productMetadata) {
        facade.demandForecasting().createProductMetadata(productMetadata);
    }

    public void createProductLifecycleStage(ProductLifecycleStage stage) {
        facade.demandForecasting().createProductLifecycleStage(stage);
    }

    public void createInventorySupply(InventorySupply inventorySupply) {
        facade.demandForecasting().createInventorySupply(inventorySupply);
    }

    public void createForecastPerformanceMetric(ForecastPerformanceMetric metric) {
        facade.demandForecasting().createForecastPerformanceMetric(metric);
    }

    public void close() {
        facade.close();
    }
}