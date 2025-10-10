-- =====================================================
-- SETUP NOTIFICATIONS LOG TABLE
-- Run this script in Supabase SQL Editor
-- =====================================================

-- 1. Create notifications_log table
CREATE TABLE IF NOT EXISTS notifications_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sender_number VARCHAR(50),
  message TEXT,
  full_text TEXT,
  timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
  device_id VARCHAR(100),
  bank_type VARCHAR(20),
  detected_amount BIGINT,
  detected_sender VARCHAR(255),
  order_id UUID REFERENCES orders(id),
  processed BOOLEAN DEFAULT false,
  note TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Enable Row Level Security
ALTER TABLE notifications_log ENABLE ROW LEVEL SECURITY;

-- 3. Create RLS Policies
-- Only authenticated users can view notifications
CREATE POLICY "Authenticated users can view notifications_log"
  ON notifications_log
  FOR SELECT
  TO authenticated
  USING (true);

-- Only service role can insert (from Edge Functions)
CREATE POLICY "Service role can insert notifications"
  ON notifications_log
  FOR INSERT
  TO service_role
  WITH CHECK (true);

-- Only service role can update (from Edge Functions)
CREATE POLICY "Service role can update notifications"
  ON notifications_log
  FOR UPDATE
  TO service_role
  USING (true)
  WITH CHECK (true);

-- 4. Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_notifications_log_timestamp ON notifications_log(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_log_processed ON notifications_log(processed);
CREATE INDEX IF NOT EXISTS idx_notifications_log_bank_type ON notifications_log(bank_type);
CREATE INDEX IF NOT EXISTS idx_notifications_log_order_id ON notifications_log(order_id);
CREATE INDEX IF NOT EXISTS idx_notifications_log_amount ON notifications_log(detected_amount);

-- 5. Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION public.handle_notifications_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 6. Create trigger for updated_at
DROP TRIGGER IF EXISTS handle_notifications_log_updated_at ON notifications_log;
CREATE TRIGGER handle_notifications_log_updated_at
  BEFORE UPDATE ON notifications_log
  FOR EACH ROW
  EXECUTE FUNCTION public.handle_notifications_updated_at();

-- 7. Create view for easy monitoring
CREATE OR REPLACE VIEW payment_notifications_summary AS
SELECT
  DATE(timestamp) as notification_date,
  bank_type,
  COUNT(*) as total_notifications,
  COUNT(CASE WHEN processed = true THEN 1 END) as processed_notifications,
  COUNT(CASE WHEN order_id IS NOT NULL THEN 1 END) as matched_orders,
  SUM(CASE WHEN processed = true THEN detected_amount ELSE 0 END) as total_verified_amount
FROM notifications_log
WHERE timestamp >= CURRENT_DATE - INTERVAL '7 days'
GROUP BY DATE(timestamp), bank_type
ORDER BY notification_date DESC, bank_type;

-- 8. Grant necessary permissions
GRANT ALL ON notifications_log TO service_role;
GRANT SELECT ON notifications_log TO authenticated;
GRANT SELECT ON payment_notifications_summary TO authenticated;