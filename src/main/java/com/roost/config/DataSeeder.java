package com.roost.config;

import com.roost.model.Property;
import com.roost.repository.PropertyRepository;
import org.slf.Logger;
import org.slf.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final PropertyRepository propertyRepository;

    public DataSeeder(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Override
    public void run(String... args) {
        if (propertyRepository.count() < 10) {
            log.info("Seeding 50 Nairobi rental property listings...");
            List<Property> seeds = new ArrayList<>();

            // 15 Kilimani Listings (~ -1.2900, 36.7820)
            seeds.add(createProperty("Sleek Studio in Kilimani", "Kilimani, Argwings Kodhek Rd", 25000, 0, "STUDIO", 1, true, true, true, true, true, false, true, "KES 25,000", "Immediate", -1.2895, 36.7822, "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=800"));
            seeds.add(createProperty("Modern 1BR Apartment Kilimani", "Kilimani, Chania Avenue", 38000, 1, "1BR", 1, true, true, true, true, true, true, true, "1 Month Rent", "1st of Next Month", -1.2910, 36.7850, "https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?w=800"));
            seeds.add(createProperty("Spacious 2BR Master Ensuite", "Kilimani, Rose Avenue", 55000, 2, "2BR", 2, true, true, true, true, true, false, true, "KES 55,000", "Immediate", -1.2880, 36.7810, "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?w=800"));
            seeds.add(createProperty("Luxury 3BR Penthouse with Pool", "Kilimani, Dennis Pritt Rd", 95000, 3, "3BR", 3, true, true, true, true, true, true, true, "2 Months Deposit", "Immediate", -1.2865, 36.7890, "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=800"));
            seeds.add(createProperty("Cozy Bedsitter near Yaya Centre", "Kilimani, Kindaruma Rd", 18000, 0, "BEDSITTER", 1, false, false, true, true, true, false, false, "KES 18,000", "Immediate", -1.2930, 36.7865, "https://images.unsplash.com/photo-1536376072261-38c75010e6c9?w=800"));
            seeds.add(createProperty("Charming 1BR Fully Furnished", "Kilimani, Lenana Rd", 45000, 1, "1BR", 1, true, true, true, true, true, false, true, "KES 45,000", "Immediate", -1.2905, 36.7875, "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=800"));
            seeds.add(createProperty("Executive 2BR Apartment", "Kilimani, George Padmore Rd", 60000, 2, "2BR", 2, true, true, true, true, true, true, true, "1 Month Rent", "Next Week", -1.2922, 36.7841, "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?w=800"));
            seeds.add(createProperty("Budget Studio near Adlife Plaza", "Kilimani, Ring Rd Kilimani", 22000, 0, "STUDIO", 1, false, true, true, true, true, false, false, "KES 22,000", "Immediate", -1.2940, 36.7880, "https://images.unsplash.com/photo-1502005229762-cf1b2da7c5d6?w=800"));
            seeds.add(createProperty("Contemporary 2BR Haven", "Kilimani, Wood Avenue", 52000, 2, "2BR", 2, true, true, true, true, true, false, true, "KES 52,000", "Immediate", -1.2918, 36.7830, "https://images.unsplash.com/photo-1513694203232-719a280e022f?w=800"));
            seeds.add(createProperty("Premium 3BR Family Flat", "Kilimani, Elgeyo Marakwet Rd", 80000, 3, "3BR", 3, true, true, true, true, true, true, true, "1 Month Rent", "Next Month", -1.2960, 36.7795, "https://images.unsplash.com/photo-1580587771525-78b9dba3b914?w=800"));
            seeds.add(createProperty("Bright 1BR with Balcony View", "Kilimani, Menelik Rd", 36000, 1, "1BR", 1, false, true, true, true, true, false, true, "KES 36,000", "Immediate", -1.2955, 36.7815, "https://images.unsplash.com/photo-1560185007-c5ca9d2c014d?w=800"));
            seeds.add(createProperty("Modern Bedsitter with Borehole Water", "Kilimani, Galana Rd", 19500, 0, "BEDSITTER", 1, false, true, true, true, true, false, false, "KES 19,500", "Immediate", -1.2885, 36.7845, "https://images.unsplash.com/photo-1493809842364-78817add7ffb?w=800"));
            seeds.add(createProperty("Upscale 2BR Apartment with Gym", "Kilimani, Tigoni Rd", 65000, 2, "2BR", 2, true, true, true, true, true, true, true, "KES 65,000", "Immediate", -1.2898, 36.7802, "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=800"));
            seeds.add(createProperty("Compact Studio Apartment", "Kilimani, Marcus Garvey Rd", 24000, 0, "STUDIO", 1, true, true, true, true, true, false, false, "KES 24,000", "Immediate", -1.2902, 36.7860, "https://images.unsplash.com/photo-1554995207-c18c203602cb?w=800"));
            seeds.add(createProperty("Grand 3BR Apartment Kilimani", "Kilimani, Cotton Avenue", 88000, 3, "3BR", 3, true, true, true, true, true, false, true, "KES 88,000", "1st of Next Month", -1.2935, 36.7825, "https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?w=800"));

            // 15 Westlands Listings (~ -1.2650, 36.8050)
            seeds.add(createProperty("High-rise 1BR in Westlands", "Westlands, Westlands Rd", 42000, 1, "1BR", 1, true, true, true, true, true, false, true, "KES 42,000", "Immediate", -1.2660, 36.8040, "https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?w=800"));
            seeds.add(createProperty("Luxury 2BR Parklands View", "Westlands, Parklands Rd", 68000, 2, "2BR", 2, true, true, true, true, true, true, true, "1 Month Deposit", "Immediate", -1.2620, 36.8090, "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?w=800"));
            seeds.add(createProperty("Modern Studio near Sarit Centre", "Westlands, Karuna Rd", 30000, 0, "STUDIO", 1, true, true, true, true, true, false, false, "KES 30,000", "Immediate", -1.2645, 36.8020, "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=800"));
            seeds.add(createProperty("Executive 3BR Duplex", "Westlands, Mpaka Rd", 110000, 3, "3BR", 3, true, true, true, true, true, true, true, "2 Months Deposit", "Immediate", -1.2675, 36.8060, "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=800"));
            seeds.add(createProperty("Affordable Bedsitter Westlands", "Westlands, Muthithi Rd", 20000, 0, "BEDSITTER", 1, false, true, true, true, true, false, false, "KES 20,000", "Immediate", -1.2690, 36.8080, "https://images.unsplash.com/photo-1536376072261-38c75010e6c9?w=800"));
            seeds.add(createProperty("Furnished 1BR Suite", "Westlands, Waiyaki Way", 50000, 1, "1BR", 1, true, true, true, true, true, false, true, "KES 50,000", "Immediate", -1.2630, 36.8010, "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=800"));
            seeds.add(createProperty("Stylish 2BR Apartment with Rooftop Lounge", "Westlands, School Lane", 75000, 2, "2BR", 2, true, true, true, true, true, true, true, "1 Month Rent", "Next Week", -1.2615, 36.8035, "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?w=800"));
            seeds.add(createProperty("Spacious Studio Flat", "Westlands, Rhapta Rd", 32000, 0, "STUDIO", 1, false, true, true, true, true, false, true, "KES 32,000", "Immediate", -1.2655, 36.7990, "https://images.unsplash.com/photo-1502005229762-cf1b2da7c5d6?w=800"));
            seeds.add(createProperty("Chic 2BR Apartment", "Westlands, Brookside Drive", 70000, 2, "2BR", 2, true, true, true, true, true, true, true, "KES 70,000", "Immediate", -1.2600, 36.7970, "https://images.unsplash.com/photo-1513694203232-719a280e022f?w=800"));
            seeds.add(createProperty("3BR Master Ensuite Family Home", "Westlands, Ring Rd Westlands", 90000, 3, "3BR", 3, true, true, true, true, true, false, true, "KES 90,000", "Immediate", -1.2680, 36.8045, "https://images.unsplash.com/photo-1580587771525-78b9dba3b914?w=800"));
            seeds.add(createProperty("Sleek 1BR Loft", "Westlands, Woodvale Grove", 46000, 1, "1BR", 1, true, true, true, true, true, false, true, "KES 46,000", "Immediate", -1.2665, 36.8055, "https://images.unsplash.com/photo-1560185007-c5ca9d2c014d?w=800"));
            seeds.add(createProperty("Bedsitter near GTC Mall", "Westlands, Chiromo Lane", 23000, 0, "BEDSITTER", 1, false, true, true, true, true, false, false, "KES 23,000", "Immediate", -1.2695, 36.8095, "https://images.unsplash.com/photo-1493809842364-78817add7ffb?w=800"));
            seeds.add(createProperty("Prime 2BR Apartment with Sauna", "Westlands, Lower Kabete Rd", 82000, 2, "2BR", 2, true, true, true, true, true, true, true, "KES 82,000", "Immediate", -1.2580, 36.7950, "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=800"));
            seeds.add(createProperty("Modern 1BR Haven", "Westlands, Church Rd", 39000, 1, "1BR", 1, false, true, true, true, true, false, false, "KES 39,000", "Immediate", -1.2605, 36.7985, "https://images.unsplash.com/photo-1554995207-c18c203602cb?w=800"));
            seeds.add(createProperty("Luxury 3BR Penthouse", "Westlands, Peponi Rd", 125000, 3, "3BR", 3, true, true, true, true, true, true, true, "2 Months Deposit", "Immediate", -1.2550, 36.7930, "https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?w=800"));

            // 10 Upperhill Listings (~ -1.2980, 36.8150)
            seeds.add(createProperty("Corporate 1BR Suite Upperhill", "Upperhill, Mara Rd", 40000, 1, "1BR", 1, true, true, true, true, true, false, true, "KES 40,000", "Immediate", -1.2970, 36.8140, "https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?w=800"));
            seeds.add(createProperty("Executive 2BR Haven", "Upperhill, Hospital Rd", 62000, 2, "2BR", 2, true, true, true, true, true, true, true, "KES 62,000", "Immediate", -1.2990, 36.8160, "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?w=800"));
            seeds.add(createProperty("Studio Apartment near KNH", "Upperhill, Ngong Rd", 26000, 0, "STUDIO", 1, false, true, true, true, true, false, false, "KES 26,000", "Immediate", -1.2985, 36.8120, "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=800"));
            seeds.add(createProperty("High-end 3BR Family Flat", "Upperhill, Elgon Rd", 85000, 3, "3BR", 3, true, true, true, true, true, false, true, "1 Month Rent", "Immediate", -1.2965, 36.8175, "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=800"));
            seeds.add(createProperty("Modern Bedsitter Upperhill", "Upperhill, Lower Hill Rd", 21000, 0, "BEDSITTER", 1, false, true, true, true, true, false, false, "KES 21,000", "Immediate", -1.3000, 36.8185, "https://images.unsplash.com/photo-1536376072261-38c75010e6c9?w=800"));
            seeds.add(createProperty("Furnished 2BR Apartment", "Upperhill, Upper Hill Rd", 70000, 2, "2BR", 2, true, true, true, true, true, true, true, "KES 70,000", "Immediate", -1.2975, 36.8155, "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=800"));
            seeds.add(createProperty("Bright 1BR Flat", "Upperhill, Chyulu Rd", 37000, 1, "1BR", 1, false, true, true, true, true, false, true, "KES 37,000", "Immediate", -1.2995, 36.8135, "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?w=800"));
            seeds.add(createProperty("Compact Studio Suite", "Upperhill, Haile Selassie Ave", 27000, 0, "STUDIO", 1, true, true, true, true, true, false, false, "KES 27,000", "Immediate", -1.2945, 36.8200, "https://images.unsplash.com/photo-1502005229762-cf1b2da7c5d6?w=800"));
            seeds.add(createProperty("Luxury 2BR Residence", "Upperhill, Bunyala Rd", 66000, 2, "2BR", 2, true, true, true, true, true, true, true, "KES 66,000", "Immediate", -1.3010, 36.8190, "https://images.unsplash.com/photo-1513694203232-719a280e022f?w=800"));
            seeds.add(createProperty("3BR Master Ensuite Apartment", "Upperhill, Ragati Rd", 88000, 3, "3BR", 3, true, true, true, true, true, false, true, "KES 88,000", "Immediate", -1.2960, 36.8145, "https://images.unsplash.com/photo-1580587771525-78b9dba3b914?w=800"));

            // 10 CBD Listings (~ -1.2850, 36.8220)
            seeds.add(createProperty("Modern CBD Studio Apartment", "CBD, Kenyatta Avenue", 28000, 0, "STUDIO", 1, true, true, true, true, true, false, false, "KES 28,000", "Immediate", -1.2840, 36.8210, "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=800"));
            seeds.add(createProperty("Convenient 1BR CBD Loft", "CBD, Moi Avenue", 35000, 1, "1BR", 1, false, true, true, true, true, false, true, "KES 35,000", "Immediate", -1.2855, 36.8240, "https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?w=800"));
            seeds.add(createProperty("Spacious 2BR City Center Flat", "CBD, University Way", 50000, 2, "2BR", 2, false, true, true, true, true, false, true, "KES 50,000", "Immediate", -1.2815, 36.8195, "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?w=800"));
            seeds.add(createProperty("Central Bedsitter near Kimathi St", "CBD, Kimathi Street", 19000, 0, "BEDSITTER", 1, false, false, true, true, true, false, false, "KES 19,000", "Immediate", -1.2835, 36.8225, "https://images.unsplash.com/photo-1536376072261-38c75010e6c9?w=800"));
            seeds.add(createProperty("Fully Furnished 1BR CBD Suite", "CBD, Koinange Street", 45000, 1, "1BR", 1, true, true, true, true, true, false, true, "KES 45,000", "Immediate", -1.2825, 36.8205, "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=800"));
            seeds.add(createProperty("Compact Studio Apartment CBD", "CBD, Tom Mboya Street", 25000, 0, "STUDIO", 1, false, true, true, true, true, false, false, "KES 25,000", "Immediate", -1.2860, 36.8260, "https://images.unsplash.com/photo-1502005229762-cf1b2da7c5d6?w=800"));
            seeds.add(createProperty("Executive 2BR CBD Apartment", "CBD, Parliament Rd", 58000, 2, "2BR", 2, true, true, true, true, true, true, true, "KES 58,000", "Immediate", -1.2880, 36.8230, "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?w=800"));
            seeds.add(createProperty("Bedsitter near Globe Cinema", "CBD, Murang'a Rd", 17500, 0, "BEDSITTER", 1, false, false, true, true, true, false, false, "KES 17,500", "Immediate", -1.2790, 36.8215, "https://images.unsplash.com/photo-1493809842364-78817add7ffb?w=800"));
            seeds.add(createProperty("Secure 1BR in City Hall area", "CBD, City Hall Way", 38000, 1, "1BR", 1, false, true, true, true, true, false, true, "KES 38,000", "Immediate", -1.2870, 36.8220, "https://images.unsplash.com/photo-1560185007-c5ca9d2c014d?w=800"));
            seeds.add(createProperty("High-rise 3BR CBD Penthouse", "CBD, Wabera Street", 78000, 3, "3BR", 3, true, true, true, true, true, true, true, "KES 78,000", "Immediate", -1.2845, 36.8212, "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=800"));

            propertyRepository.saveAll(seeds);
            log.info("Successfully seeded 50 Nairobi properties!");
        }
    }

    private Property createProperty(String title, String location, double price, int bedrooms, String houseType,
                                     int bathrooms, boolean furnished, boolean parking, boolean wifi,
                                     boolean water, boolean security, boolean petFriendly, boolean balcony,
                                     String deposit, String moveInDate, double lat, double lng, String imageUrl) {
        Property p = new Property();
        p.setTitle(title);
        p.setLocation(location);
        p.setPrice(price);
        p.setBedrooms(bedrooms);
        p.setType("RENTAL");
        p.setHouseType(houseType);
        p.setBathrooms(bathrooms);
        p.setFurnished(furnished);
        p.setParking(parking);
        p.setWifi(wifi);
        p.setWater(water);
        p.setSecurity(security);
        p.setPetFriendly(petFriendly);
        p.setBalcony(balcony);
        p.setDeposit(deposit);
        p.setMoveInDate(moveInDate);
        p.setAvailable(true);
        p.setVerified(true);
        p.setLandlordName("Roost Verified Landlord");
        p.setLandlordPhone("+254 712 345 678");
        p.setLatitude(lat);
        p.setLongitude(lng);
        p.setImageUrl(imageUrl);
        p.setImageUrls(List.of(imageUrl, "https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?w=800", "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?w=800"));
        p.setDescription("A premium verified rental property in " + location + ". Features 24hr water, high speed internet, top security, and clean modern finishes.");
        p.setListedAt(LocalDateTime.now());
        p.setLastConfirmedAt(LocalDateTime.now());
        return p;
    }
}
