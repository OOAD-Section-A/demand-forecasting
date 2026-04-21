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

    public void deleteForecast(String forecastId) {
        adapter.deleteForecast(forecastId);
    }

    public void createSalesRecord(SalesRecord salesRecord) {
        adapter.createSalesRecord(salesRecord);
    }

    public void deleteSalesRecord(String saleId) {
        adapter.deleteSalesRecord(saleId);
    }

    public void createHolidayCalendar(HolidayCalendar holidayCalendar) {
        adapter.createHolidayCalendar(holidayCalendar);
    }

    public void deleteHolidayCalendar(String holidayId) {
        adapter.deleteHolidayCalendar(holidayId);
    }

    public void createPromotionalCalendar(PromotionalCalendar promotionalCalendar) {
        adapter.createPromotionalCalendar(promotionalCalendar);
    }

    public void deletePromotionalCalendar(String promoCalendarId) {
        adapter.deletePromotionalCalendar(promoCalendarId);
    }

    public void createProductMetadata(ProductMetadata productMetadata) {
        adapter.createProductMetadata(productMetadata);
    }

    public void deleteProductMetadata(String productId) {
        adapter.deleteProductMetadata(productId);
    }

    public void createProductLifecycleStage(ProductLifecycleStage stage) {
        adapter.createProductLifecycleStage(stage);
    }

    public void deleteProductLifecycleStage(String lifecycleId) {
        adapter.deleteProductLifecycleStage(lifecycleId);
    }

    public void createInventorySupply(InventorySupply inventorySupply) {
        adapter.createInventorySupply(inventorySupply);
    }

    public void deleteInventorySupply(String productId) {
        adapter.deleteInventorySupply(productId);
    }

    public void createForecastPerformanceMetric(ForecastPerformanceMetric metric) {
        adapter.createForecastPerformanceMetric(metric);
    }

    public void deleteForecastPerformanceMetric(String evalId) {
        adapter.deleteForecastPerformanceMetric(evalId);
    }

    public void createForecastTimeseries(ForecastTimeseries ts) {
        adapter.createForecastTimeseries(ts);
    }

    public void deleteForecastTimeseries(String timeseriesId) {
        adapter.deleteForecastTimeseries(timeseriesId);
    }

    public void createBatchForecastTimeseries(List<ForecastTimeseries> list) {
        adapter.createBatchForecastTimeseries(list);
    }

    public List<SalesRecord> listSalesRecords() {
        return facade.demandForecasting().listSalesRecords();
    }
}
