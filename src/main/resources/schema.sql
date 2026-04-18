

create database demandforecastinglocal;

use demandforecastinglocal;

CREATE TABLE IF NOT EXISTS sales_records (
    sale_id                          VARCHAR(50)    NOT NULL,
    product_id                       VARCHAR(50)    NOT NULL,
    store_id                         VARCHAR(50)    NOT NULL,
    sale_date                        DATE           NOT NULL,
    quantity_sold                    INT            NOT NULL,
    unit_price                       DECIMAL(12,2)  NOT NULL,
    revenue                          DECIMAL(14,2)  NOT NULL,
    region                           VARCHAR(50)    NULL,

    PRIMARY KEY (sale_id),
    CONSTRAINT chk_sales_qty CHECK (quantity_sold >= 0),
    CONSTRAINT chk_sales_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_sales_revenue CHECK (revenue >= 0)
) COMMENT 'Historical sales inputs for forecasting models';


CREATE TABLE IF NOT EXISTS holiday_calendar (
    holiday_id                       VARCHAR(50)    NOT NULL,
    holiday_date                     DATE           NOT NULL,
    holiday_name                     VARCHAR(100)   NOT NULL,
    holiday_type                     VARCHAR(50)    NOT NULL,
    region_applicable                VARCHAR(50)    NULL,

    PRIMARY KEY (holiday_id)
) COMMENT 'Holiday and calendar features for demand forecasting';


CREATE TABLE IF NOT EXISTS promotional_calendar (
    promo_calendar_id                VARCHAR(50)    NOT NULL,
    promo_id                         VARCHAR(50)    NULL,
    promo_name                       VARCHAR(100)   NOT NULL,
    promo_start_date                 DATE           NOT NULL,
    promo_end_date                   DATE           NOT NULL,
    discount_percentage              DECIMAL(5,2)   NULL,
    promo_type                       VARCHAR(50)    NULL,
    applicable_products              TEXT           NULL,

    PRIMARY KEY (promo_calendar_id),
    CONSTRAINT chk_promo_calendar_range CHECK (promo_end_date >= promo_start_date)
) COMMENT 'Promotional events used as demand-forecasting features';

CREATE TABLE IF NOT EXISTS product_metadata (
    product_id                       VARCHAR(50)    NOT NULL,
    product_name                     VARCHAR(150)   NOT NULL,
    category                         VARCHAR(100)   NOT NULL,
    sub_category                     VARCHAR(100)   NULL,
    seasonality_type                 VARCHAR(100)   NULL,

    PRIMARY KEY (product_id)
) COMMENT 'Product metadata used by demand forecasting';


CREATE TABLE IF NOT EXISTS product_lifecycle_stages (
    lifecycle_id                     VARCHAR(50)    NOT NULL,
    product_id                       VARCHAR(50)    NOT NULL,
    current_stage                    VARCHAR(50)    NOT NULL,
    stage_start_date                 DATE           NOT NULL,
    previous_stage                   VARCHAR(50)    NULL,
    transition_date                  DATE           NULL,

    PRIMARY KEY (lifecycle_id)
) COMMENT 'Lifecycle-stage metadata for products in forecasting';

CREATE TABLE IF NOT EXISTS inventory_supply (
    product_id                       VARCHAR(50)    NOT NULL,
    current_stock                    INT            NULL,
    reorder_point                    INT            NULL,
    lead_time_days                   INT            NULL,
    supplier_id                      VARCHAR(50)    NULL,

    PRIMARY KEY (product_id)
) COMMENT 'Inventory and supply features used by forecasting';

-- -------------------------------------------------------
-- Component — Forecast Output (Core Table)
-- Stores generated demand forecasts used by inventory,
-- procurement, and reporting subsystems.
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS demand_forecasts (
    forecast_id              VARCHAR(50)    NOT NULL,
    product_id               VARCHAR(50)    NOT NULL COMMENT 'Ref to Inventory subsystem',
    forecast_period          VARCHAR(30)    NOT NULL COMMENT 'e.g. WEEKLY, MONTHLY',
    forecast_date            DATE           NULL COMMENT 'Date for which prediction applies',
    predicted_demand         INT            NOT NULL COMMENT 'Model output for expected demand',
    confidence_score         DECIMAL(5,2)   NULL COMMENT 'Prediction confidence (0–100)',
    reorder_signal           BOOLEAN        NOT NULL DEFAULT FALSE COMMENT 'Triggers reorder if TRUE',
    suggested_order_qty      INT            NULL COMMENT 'Recommended replenishment quantity',
    lifecycle_stage          VARCHAR(50)    NULL COMMENT 'Derived from lifecycle metadata',
    algorithm_used           VARCHAR(100)   NULL COMMENT 'Model used (ARIMA, LSTM, etc.)',
    generated_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_event_reference   VARCHAR(100),
    PRIMARY KEY (forecast_id),

    CONSTRAINT chk_predicted_qty_positive CHECK (predicted_demand >= 0),
    CONSTRAINT chk_confidence_range CHECK (confidence_score IS NULL 
                                          OR confidence_score BETWEEN 0 AND 100),
    CONSTRAINT chk_suggested_qty CHECK (suggested_order_qty IS NULL 
                                       OR suggested_order_qty >= 0)
) COMMENT 'Core demand forecasting output table used across subsystems';

CREATE TABLE IF NOT EXISTS forecast_performance_metrics (
    eval_id                          VARCHAR(50)    NOT NULL,
    forecast_id                      VARCHAR(50)    NOT NULL,
    forecast_date                    DATE           NOT NULL,
    predicted_qty                    INT            NOT NULL,
    actual_qty                       INT            NULL,
    mape                             DECIMAL(8,2)   NULL,
    rmse                             DECIMAL(12,4)  NULL,
    model_used                       VARCHAR(100)   NULL,

    PRIMARY KEY (eval_id),
    FOREIGN KEY (forecast_id) REFERENCES demand_forecasts(forecast_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_predicted_qty_nonneg CHECK (predicted_qty >= 0),
    CONSTRAINT chk_actual_qty_nonneg CHECK (actual_qty IS NULL OR actual_qty >= 0)
) COMMENT 'Evaluation metrics for generated demand forecasts';

CREATE TABLE IF NOT EXISTS barcode_rfid_events (
    event_id VARCHAR(50) NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    rfid_tag VARCHAR(100) NULL,
    product_name VARCHAR(150) NULL,
    category VARCHAR(100) NULL,
    description TEXT NULL,
    transaction_id VARCHAR(50) NULL,
    warehouse_id VARCHAR(50) NULL,
    event_timestamp DATETIME NOT NULL,
    status VARCHAR(50) NOT NULL,
    source VARCHAR(100) NOT NULL,
    PRIMARY KEY (event_id)
);

CREATE TABLE IF NOT EXISTS subsystem_exceptions (
    exception_id VARCHAR(50) NOT NULL,
    exception_name VARCHAR(150) NULL,
    subsystem_name VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    timestamp_utc DATETIME NOT NULL,
    duration_ms BIGINT NULL,
    exception_message VARCHAR(500) NOT NULL,
    error_code BIGINT NULL,
    stack_trace TEXT NULL,
    inner_exception TEXT NULL,
    user_account VARCHAR(150) NULL,
    handling_plan TEXT NULL,
    retry_count TINYINT UNSIGNED NULL,
    status VARCHAR(30) NOT NULL,
    resolved_at DATETIME NULL,
    PRIMARY KEY (exception_id)
);


show tables;
