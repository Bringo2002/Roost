-- Seed images and descriptions for existing properties
-- Run against the 'roost' PostgreSQL database after restarting Spring Boot
-- (Hibernate ddl-auto=update will have created the columns automatically)

-- NOTE: These Unsplash URLs are free-to-use, no API key needed.
-- Adjust the WHERE clauses to match your actual property titles/IDs.

-- Generic updates by ID (adjust IDs to match your data)
UPDATE properties SET
  image_url = 'https://images.unsplash.com/photo-1564013799919-ab600027ffc6?w=800&q=80',
  description = 'Modern apartment with spacious living areas, natural light throughout, and a fully fitted kitchen. Located in a secure compound with 24-hour security, ample parking, and easy access to shopping centres and public transport.'
WHERE id = 1;

UPDATE properties SET
  image_url = 'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=800&q=80',
  description = 'Elegant townhouse in a quiet, leafy neighbourhood. Features include a private garden, open-plan living and dining, modern finishes, and a dedicated home office space. Walking distance to international schools.'
WHERE id = 2;

UPDATE properties SET
  image_url = 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=800&q=80',
  description = 'Stylish studio apartment ideal for young professionals. Comes fully furnished with high-speed internet, air conditioning, and access to a rooftop terrace with panoramic city views.'
WHERE id = 3;

UPDATE properties SET
  image_url = 'https://images.unsplash.com/photo-1605276374104-dee2a0ed3cd6?w=800&q=80',
  description = 'Spacious family home set on a large plot with mature trees. Four bedrooms, each with en-suite bathrooms, a separate staff quarter, and a double garage. Perfect for families seeking peace and security.'
WHERE id = 4;

UPDATE properties SET
  image_url = 'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?w=800&q=80',
  description = 'Contemporary 2-bedroom apartment in a newly built complex. Features include a gym, swimming pool, backup generator, and borehole water. Close to Yaya Centre and Hurlingham shopping areas.'
WHERE id = 5;

UPDATE properties SET
  image_url = 'https://images.unsplash.com/photo-1600566753190-17f0baa2a6c3?w=800&q=80',
  description = 'Cosy bedsitter in a well-maintained building with reliable water and electricity. Ideal for students and young professionals. Near public transport routes and local amenities.'
WHERE id = 6;

-- If you have more than 6 properties, add more rows here.
-- You can also update by title if IDs don't match:
--
-- UPDATE properties SET
--   image_url = 'https://images.unsplash.com/photo-XXXX?w=800&q=80',
--   description = 'Your description here'
-- WHERE title = 'Your Property Title';
