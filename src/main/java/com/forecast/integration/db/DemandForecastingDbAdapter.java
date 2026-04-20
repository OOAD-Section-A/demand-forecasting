package com.forecast.integration.db;

import com.jackfruit.scm.database.adapter.DemandForecastingAdapter;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.DemandForecast;
import com.jackfruit.scm.database.model.ForecastTimeseries;
import com.jackfruit.scm.database.model.DemandForecastingModels.*;

import java.util.List;

public class DemandForecastingDbAdapter {

    private final SupplyChainDatabaseFacade facade;
    private final DemandForecastingAdapter adapter;

    public DemandForecastingDbAdapter(SupplyChainDatabaseFacade facade) {
        this.facade = facade;
        this.adapter = new DemandForecastingAdapter(facade);
    }

    public void createForecast(DemandForecast forecast) {
        adapter.createForecast(forecast);
    }

    public void createSalesRecord(SalesRecord salesRecord) {
        adapter.createSalesRecord(salesRecord);
    }

    public void createHolidayCalendar(HolidayCalendar holidayCalendar) {
        adapter.createHolidayCalendar(holidayCalendar);
    }

    public void createPromotionalCalendar(PromotionalCalendar promotionalCalendar) {
        adapter.createPromotionalCalendar(promotionalCalendar);
    }

    public void createProductMetadata(ProductMetadata productMetadata) {
        adapter.createProductMetadata(productMetadata);
    }

    public void createProductLifecycleStage(ProductLifecycleStage stage) {
        adapter.createProductLifecycleStage(stage);
    }

    public void createInventorySupply(InventorySupply inventorySupply) {
        adapter.createInventorySupply(inventorySupply);
    }

    public void createForecastPerformanceMetric(ForecastPerformanceMetric metric) {
        adapter.createForecastPerformanceMetric(metric);
    }

    public void createForecastTimeseries(ForecastTimeseries ts) {
        adapter.createForecastTimeseries(ts);
    }

    public void createBatchForecastTimeseries(List<ForecastTimeseries> list) {
        adapter.createBatchForecastTimeseries(list);
    }

    public List<SalesRecord> listSalesRecords() {
        return facade.demandForecasting().listSalesRecords();
    }
}
