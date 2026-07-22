-- TICKET-ADV009 — Sample JSONB payloads

UPDATE instruments
SET metadata = '{
  "sector": "Technology",
  "exchange": "XETR",
  "issuer": {
    "name": "SAP SE",
    "country": "DE",
    "lei": "529900D6BF99LW9R2E68"
  },
  "rating": {
    "sp": "AA-",
    "moody": "Aa3"
  },
  "tags": ["DAX40", "ESG-tier-1"]
}'::JSONB
WHERE symbol = 'SAP.DE';


UPDATE instruments
SET metadata = '{
  "sector": "Energy",
  "underlying": "WTI",
  "contractSize": 1000,
  "expiryMonth": "2026-12",
  "tags": ["futures", "physical-settlement"]
}'::JSONB
WHERE symbol = 'CL_FUT';


-- Containment (uses GIN):
SELECT
    symbol,
    metadata->>'sector' AS sector
FROM instruments
WHERE metadata @> '{"sector": "Technology"}';


-- Path extraction:
SELECT
    symbol,
    metadata->'issuer'->>'country' AS country
FROM instruments;


-- Array membership:
SELECT
    symbol
FROM instruments
WHERE metadata->'tags' ? 'DAX40';


-- Existence:
SELECT
    symbol
FROM instruments
WHERE metadata ? 'rating';