-- Admin payment history lists newest first and filters by the day a payment was created.
ALTER TABLE payment_transactions ADD INDEX idx_payment_transactions_created (created_at, id);
