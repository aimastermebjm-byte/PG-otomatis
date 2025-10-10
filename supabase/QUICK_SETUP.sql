-- =====================================================
-- QUICK SETUP - Run ini di Supabase SQL Editor
-- =====================================================

-- 1. Buat tabel orders
CREATE TABLE IF NOT EXISTS orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  userid UUID REFERENCES auth.users(id),
  userEmail VARCHAR(255),
  items JSONB,
  subtotal BIGINT,
  uniqueCode SMALLINT,
  total BIGINT,
  status VARCHAR(20) DEFAULT 'PENDING',
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  paidAt TIMESTAMP WITH TIME ZONE,
  paymentDetails JSONB
);

-- 2. Buat tabel user_profiles
CREATE TABLE IF NOT EXISTS user_profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
  email VARCHAR(255),
  full_name VARCHAR(255),
  phone VARCHAR(20),
  birth_date DATE,
  gender VARCHAR(10) CHECK (gender IN ('male', 'female', 'other')),
  address TEXT,
  city VARCHAR(100),
  postal_code VARCHAR(10),
  profile_photo_url VARCHAR(500),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  UNIQUE(user_id)
);

-- 3. Buat tabel notifications_log
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
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 4. Enable RLS
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications_log ENABLE ROW LEVEL SECURITY;

-- 5. Policies untuk orders
CREATE POLICY "Users can view own orders" ON orders
  FOR SELECT USING (auth.uid() = userid);

CREATE POLICY "Users can insert own orders" ON orders
  FOR INSERT WITH CHECK (auth.uid() = userid);

-- 6. Policies untuk user_profiles
CREATE POLICY "Users can view own profile" ON user_profiles
  FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own profile" ON user_profiles
  FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own profile" ON user_profiles
  FOR UPDATE USING (auth.uid() = user_id);

-- 7. Function untuk auto create profile
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.user_profiles (user_id, email)
  VALUES (new.id, new.email);
  RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 8. Trigger untuk auto profile
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- 9. Storage bucket untuk profile photos
INSERT INTO storage.buckets (id, name, public)
VALUES ('profile-photos', 'profile-photos', true)
ON CONFLICT (id) DO NOTHING;

-- 10. Policies untuk storage
CREATE POLICY "Users can upload own photo" ON storage.objects
  FOR INSERT WITH CHECK (
    bucket_id = 'profile-photos' AND
    auth.uid()::text = (storage.foldername(name))[1]
  );

CREATE POLICY "Users can view own photo" ON storage.objects
  FOR SELECT USING (
    bucket_id = 'profile-photos' AND
    auth.uid()::text = (storage.foldername(name))[1]
  );

-- 11. Grant permissions
GRANT ALL ON orders TO authenticated;
GRANT ALL ON user_profiles TO authenticated;
GRANT SELECT ON notifications_log TO authenticated;