-- V39: Add fulfillment_status to orders table for CLICK_AND_COLLECT (BOPIS) orders.
--
-- FulfillmentStatus lifecycle: RECEIVED → PREPARING → READY_FOR_PICKUP → PICKED_UP
-- Exception paths: any state → EXPIRED; RECEIVED/PREPARING → CANCELLED
--
-- Only populated for orders where order_type = 'CLICK_AND_COLLECT'.
-- NULL for standard (dine-in, take-away, delivery) orders.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS fulfillment_status TEXT DEFAULT NULL;

COMMENT ON COLUMN orders.fulfillment_status IS
    'Fulfillment lifecycle state for CLICK_AND_COLLECT orders: RECEIVED|PREPARING|READY_FOR_PICKUP|PICKED_UP|EXPIRED|CANCELLED';
