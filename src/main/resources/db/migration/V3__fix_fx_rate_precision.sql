-- Fix fx_quotes rate precision to support small rates like 0.00058
-- The column was created with NUMERIC(18,8) but we need to ensure it's correct
ALTER TABLE fx_quotes ALTER COLUMN rate TYPE NUMERIC(18,8);
