-- Add reserved quantity for order reservations

ALTER TABLE stock
    ADD COLUMN reserved_quantity INTEGER NOT NULL DEFAULT 0;
