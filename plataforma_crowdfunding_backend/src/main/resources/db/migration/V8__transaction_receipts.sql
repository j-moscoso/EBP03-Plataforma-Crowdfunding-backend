CREATE TABLE transaction_receipts (
    id UUID PRIMARY KEY,
    contribution_id UUID NOT NULL UNIQUE REFERENCES contributions(id) ON DELETE CASCADE,
    reference_code VARCHAR(80) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('HELD', 'SUCCESSFUL', 'REFUNDED', 'FAILED')),
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    refunded_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_transaction_receipts_status ON transaction_receipts (status);

INSERT INTO transaction_receipts (id, contribution_id, reference_code, status, issued_at, updated_at)
SELECT c.id, c.id, 'REF-' || CAST(c.id AS VARCHAR), 'SUCCESSFUL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM contributions c
WHERE c.status = 'CONFIRMED'
  AND EXISTS (
      SELECT 1
      FROM payments p
      WHERE p.contribution_id = c.id
        AND p.status = 'SUCCEEDED'
  );
