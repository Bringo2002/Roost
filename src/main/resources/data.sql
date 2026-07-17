-- Sample properties for Roost (Nairobi, Kenya)
-- Using direct Unsplash stock photos to make the UI look premium and realistic
-- Walkthrough Video links included for selected premium properties

INSERT INTO properties (title, location, price, bedrooms, type, available, landlord_phone, description, image_url, verified, holding_fee_paid, latitude, longitude, video_url)
SELECT 'Modern Studio Apartment', 'Westlands, Nairobi', 35000, 1, 'rental', true, '+254712345678',
       'A sleek, fully-furnished studio in the heart of Westlands. Walking distance to Sarit Centre. Features include high-speed Wi-Fi, 24/7 security, and rooftop access with city views.',
       'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=800&q=80', true, false, -1.2673, 36.8114,
       'https://assets.mixkit.co/videos/preview/mixkit-interior-of-a-modern-apartment-43037-large.mp4'
WHERE NOT EXISTS (SELECT 1 FROM properties WHERE title = 'Modern Studio Apartment' AND location = 'Westlands, Nairobi');

UPDATE properties 
SET image_url = 'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=800&q=80',
    video_url = 'https://assets.mixkit.co/videos/preview/mixkit-interior-of-a-modern-apartment-43037-large.mp4'
WHERE title = 'Modern Studio Apartment' AND location = 'Westlands, Nairobi';


INSERT INTO properties (title, location, price, bedrooms, type, available, landlord_phone, description, image_url, verified, holding_fee_paid, latitude, longitude, video_url)
SELECT 'Spacious 3BR Family Home', 'Karen, Nairobi', 120000, 3, 'rental', true, '+254723456789',
       'Beautiful 3-bedroom house in serene Karen. Large garden, servant quarters, secure compound with electric fence. Close to Karen Country Club and The Hub mall.',
       'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=800&q=80', true, false, -1.3187, 36.7117, NULL
WHERE NOT EXISTS (SELECT 1 FROM properties WHERE title = 'Spacious 3BR Family Home' AND location = 'Karen, Nairobi');

UPDATE properties 
SET image_url = 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=800&q=80'
WHERE title = 'Spacious 3BR Family Home' AND location = 'Karen, Nairobi';


INSERT INTO properties (title, location, price, bedrooms, type, available, landlord_phone, description, image_url, verified, holding_fee_paid, latitude, longitude, video_url)
SELECT 'Luxury Penthouse', 'Kilimani, Nairobi', 250000, 4, 'rental', true, '+254734567890',
       'Stunning penthouse apartment with panoramic views of Nairobi. Open-plan living, private terrace, gym, swimming pool, and concierge service. Premium finishes throughout.',
       'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=800&q=80', false, false, -1.2889, 36.7863, NULL
WHERE NOT EXISTS (SELECT 1 FROM properties WHERE title = 'Luxury Penthouse' AND location = 'Kilimani, Nairobi');

UPDATE properties 
SET image_url = 'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=800&q=80'
WHERE title = 'Luxury Penthouse' AND location = 'Kilimani, Nairobi';


INSERT INTO properties (title, location, price, bedrooms, type, available, landlord_phone, description, image_url, verified, holding_fee_paid, latitude, longitude, video_url)
SELECT 'Cozy Bedsitter', 'Roysambu, Nairobi', 12000, 1, 'rental', true, '+254745678901',
       'Affordable bedsitter ideal for students and young professionals. Near TRM mall and public transport. Water and security included in rent.',
       'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=800&q=80', true, false, -1.2181, 36.8876, NULL
WHERE NOT EXISTS (SELECT 1 FROM properties WHERE title = 'Cozy Bedsitter' AND location = 'Roysambu, Nairobi');

UPDATE properties 
SET image_url = 'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=800&q=80'
WHERE title = 'Cozy Bedsitter' AND location = 'Roysambu, Nairobi';


INSERT INTO properties (title, location, price, bedrooms, type, available, landlord_phone, description, image_url, verified, holding_fee_paid, latitude, longitude, video_url)
SELECT '4BR Villa for Sale', 'Runda, Nairobi', 45000000, 4, 'sale', true, '+254756789012',
       'Executive 4-bedroom villa in prestigious Runda estate. Double garage, mature garden, staff quarters, borehole water. Title deed ready for transfer.',
       'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=800&q=80', true, false, -1.2216, 36.8032, NULL
WHERE NOT EXISTS (SELECT 1 FROM properties WHERE title = '4BR Villa for Sale' AND location = 'Runda, Nairobi');

UPDATE properties 
SET image_url = 'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=800&q=80'
WHERE title = '4BR Villa for Sale' AND location = 'Runda, Nairobi';


INSERT INTO properties (title, location, price, bedrooms, type, available, landlord_phone, description, image_url, verified, holding_fee_paid, latitude, longitude, video_url)
SELECT 'Stylish Airbnb Loft', 'Lavington, Nairobi', 8500, 2, 'airbnb', true, '+254767890123',
       'Trendy loft-style Airbnb in Lavington. Perfect for short stays. Netflix, fast Wi-Fi, fully-equipped kitchen, and free parking. Self check-in available.',
       'https://images.unsplash.com/photo-1505691938895-1758d7feb511?auto=format&fit=crop&w=800&q=80', true, false, -1.2827, 36.7686, NULL
WHERE NOT EXISTS (SELECT 1 FROM properties WHERE title = 'Stylish Airbnb Loft' AND location = 'Lavington, Nairobi');

UPDATE properties 
SET image_url = 'https://images.unsplash.com/photo-1505691938895-1758d7feb511?auto=format&fit=crop&w=800&q=80'
WHERE title = 'Stylish Airbnb Loft' AND location = 'Lavington, Nairobi';


INSERT INTO properties (title, location, price, bedrooms, type, available, landlord_phone, description, image_url, verified, holding_fee_paid, latitude, longitude, video_url)
SELECT '2BR Apartment Kileleshwa', 'Kileleshwa, Nairobi', 65000, 2, 'rental', false, '+254778901234',
       'Modern 2-bedroom apartment in Kileleshwa. Master ensuite, open kitchen, balcony, gym, and swimming pool. Secure parking for two vehicles.',
       'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80', true, true, -1.2785, 36.7777, NULL
WHERE NOT EXISTS (SELECT 1 FROM properties WHERE title = '2BR Apartment Kileleshwa' AND location = 'Kileleshwa, Nairobi');

UPDATE properties 
SET image_url = 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80'
WHERE title = '2BR Apartment Kileleshwa' AND location = 'Kileleshwa, Nairobi';


INSERT INTO properties (title, location, price, bedrooms, type, available, landlord_phone, description, image_url, verified, holding_fee_paid, latitude, longitude, video_url)
SELECT '1BR Serviced Apartment', 'CBD, Nairobi', 5000, 1, 'airbnb', true, '+254789012345',
       'Centrally located serviced apartment in Nairobi CBD. Daily housekeeping, breakfast available, business center access. Ideal for business travelers.',
       'https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=800&q=80', false, false, -1.2864, 36.8172, NULL
WHERE NOT EXISTS (SELECT 1 FROM properties WHERE title = '1BR Serviced Apartment' AND location = 'CBD, Nairobi');

UPDATE properties 
SET image_url = 'https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=800&q=80'
WHERE title = '1BR Serviced Apartment' AND location = 'CBD, Nairobi';
