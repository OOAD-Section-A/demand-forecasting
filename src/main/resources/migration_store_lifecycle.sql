-- =============================================================================
-- Migration: store_lifecycle_overrides
--
-- Adds per-store lifecycle stage overrides for the Demand Forecasting subsystem.
-- When a row exists here for (product_id, store_id), LifeCycleManager uses it
-- instead of the product-level row in product_lifecycle_stages.
--
-- This enables scenarios like:
--   - A product that is in MATURITY nationally but in INTRODUCTION in a new region
--   - A product being phased out (DECLINE) at specific stores before the overall
--     lifecycle transition is recorded globally
--
-- Run against the OOAD database:
--   mysql -u root -p OOAD < migration_store_lifecycle.sql
-- =============================================================================

USE OOAD;

CREATE TABLE IF NOT EXISTS store_lifecycle_overrides (
    override_id            VARCHAR(50)   NOT NULL,
    product_id             VARCHAR(50)   NOT NULL  COMMENT 'Ref to product_lifecycle_stages',
    store_id               VARCHAR(50)   NOT NULL  COMMENT 'Store this override applies to',
    current_stage          VARCHAR(50)   NOT NULL  COMMENT 'INTRODUCTION|GROWTH|MATURITY|DECLINE|DISCONTINUED',
    override_start_date    DATE          NOT NULL  COMMENT 'Date from which this override is effective',
    override_end_date      DATE          NULL      COMMENT 'NULL = open-ended override',
    created_by             VARCHAR(100)  NULL      COMMENT 'User or system that created the override',
    notes                  TEXT          NULL      COMMENT 'Reason for the store-specific override',
    created_at             DATETIME      NOT NULL  DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (override_id),

    -- Only one active override per product-store combination at a time
    UNIQUE KEY uq_store_lifecycle_override
        (product_id, store_id, override_start_date),

    CONSTRAINT chk_store_lifecycle_stage
        CHECK (current_stage IN ('INTRODUCTION','GROWTH','MATURITY','DECLINE','DISCONTINUED')),

    CONSTRAINT chk_store_override_date_range
        CHECK (override_end_date IS NULL OR override_end_date >= override_start_date)

) COMMENT 'Per-store lifecycle stage overrides consumed by LifeCycleManager';


-- =============================================================================
-- Example rows (comment out before running in production)
-- =============================================================================
-- INSERT INTO store_lifecycle_overrides
--     (override_id, product_id, store_id, current_stage, override_start_date, notes)
-- VALUES
--     ('OVR-001', 'P1001', 'S005', 'INTRODUCTION', '2025-01-01',
--      'New market entry — S005 is a pilot store for this product'),
--     ('OVR-002', 'P2034', 'S012', 'DECLINE', '2024-10-01',
--      'Product being phased out at S012 ahead of national discontinuation');