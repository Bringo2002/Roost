package com.roost.service;

import com.roost.model.Property;
import com.roost.repository.PropertyRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Server-side replacement for what used to be RentEstimatorService.dart
 * on the Flutter client. That version fetched up to 50 full property
 * records over the network on every single detail-page view and
 * recomputed percentiles and value-drivers on-device -- a real
 * bandwidth/latency cost, and (via its own separate comparable-matching
 * logic: house type + first word of location, no bedroom filter at all)
 * a second, independent "is this price fair?" system that could
 * disagree with PropertyRiskService's price-comparison feature for the
 * exact same listing.
 *
 * This uses the identical comparable-matching approach
 * PropertyRiskService already established (house type + bedrooms + GPS
 * distance, falling back to exact-string location) so there is exactly
 * one definition of "comparable" in the app, computed once server-side
 * and returned as a handful of numbers instead of dozens of full
 * property payloads.
 */
@Service
public class RentEstimateService {

    private static final int MAX_COMPARABLES = 100;
    private static final double COMPARABLE_RADIUS_KM = 2.0;
    private static final int MIN_COMPARABLES_REQUIRED = 3;

    private final PropertyRepository propertyRepository;

    public RentEstimateService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    public record ValueDriver(String label, String impact, boolean isPositive) {}

    public record RentEstimate(
            double minPrice,
            double medianPrice,
            double maxPrice,
            String rating,
            double percentile,
            int comparableCount,
            List<ValueDriver> valueDrivers) {}

    /** Returns null when there aren't enough comparables to say
     *  anything meaningful -- matches the original client's "Not Enough
     *  Data" state rather than fabricating a range from 1-2 listings. */
    public RentEstimate estimate(Property property) {
        List<Property> comps = findComparables(property);
        if (comps.size() < MIN_COMPARABLES_REQUIRED) {
            return null;
        }

        List<Double> prices = comps.stream().map(Property::getPrice).sorted().toList();
        double p25 = percentile(prices, 0.25);
        double p50 = percentile(prices, 0.50);
        double p75 = percentile(prices, 0.75);

        // Recalibrated from the original logic, which labeled anything
        // at or below the median -- the cheaper half of ALL comparables,
        // by definition -- a "Great Deal." That's not a rarity signal,
        // just "below average," and overstates what it tells a tenant.
        // Reserving GREAT_DEAL for the bottom quartile makes the label
        // mean something.
        String rating;
        if (property.getPrice() <= p25) {
            rating = "GREAT_DEAL";
        } else if (property.getPrice() <= p75) {
            rating = "FAIR_PRICE";
        } else {
            rating = "ABOVE_MARKET";
        }

        long atOrBelow = prices.stream().filter(p -> p <= property.getPrice()).count();
        double percentileRank = (double) atOrBelow / prices.size();

        List<ValueDriver> drivers = analyzeValueDrivers(property, comps);

        return new RentEstimate(p25, p50, p75, rating, percentileRank, comps.size(), drivers);
    }

    private List<Property> findComparables(Property property) {
        long excludeId = property.getId() != null ? property.getId() : -1L;
        Pageable cap = PageRequest.of(0, MAX_COMPARABLES);

        Double lat = property.getLatitude();
        Double lng = property.getLongitude();
        if (lat != null && lng != null) {
            List<Property> byDistance = propertyRepository.findComparablePropertiesByDistance(
                    property.getHouseType(), property.getBedrooms(), lat, lng, COMPARABLE_RADIUS_KM, excludeId, cap);
            if (byDistance.size() >= MIN_COMPARABLES_REQUIRED) {
                return byDistance;
            }
        }

        return propertyRepository.findComparableProperties(
                property.getHouseType(), property.getBedrooms(), property.getLocation(), excludeId, cap);
    }

    /** Linear-interpolation percentile, matching the standard
     *  definition (same one Postgres's percentile_cont uses) rather
     *  than a naive index lookup, so results don't jump discontinuously
     *  as the comparable set size changes by one. */
    private double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0.0;
        if (sorted.size() == 1) return sorted.get(0);
        if (p <= 0) return sorted.get(0);
        if (p >= 1) return sorted.get(sorted.size() - 1);

        double position = p * (sorted.size() - 1);
        int index = (int) Math.floor(position);
        double fraction = position - index;
        return sorted.get(index) + fraction * (sorted.get(index + 1) - sorted.get(index));
    }

    /** Surfaces up to 5 amenities that plausibly explain why this
     *  listing sits where it does relative to comparables: amenities
     *  it has that most comparables lack (positive drivers) or lacks
     *  that most comparables have (negative drivers). Prevalence
     *  thresholds and impact-range labels are illustrative estimates,
     *  not derived from an actual price-regression model -- there's no
     *  claim here beyond "this is unusually rare/common among similar
     *  listings." */
    private List<ValueDriver> analyzeValueDrivers(Property property, List<Property> comps) {
        int total = comps.size();
        if (total == 0) return List.of();

        List<Map.Entry<ValueDriver, Double>> candidates = new ArrayList<>();
        addDriverIfNotable(candidates, comps, total, property.isParking(), "Dedicated Parking", Property::isParking);
        addDriverIfNotable(candidates, comps, total, property.isFurnished(), "Fully Furnished", Property::isFurnished);
        addDriverIfNotable(candidates, comps, total, property.isWifi(), "WiFi Included", Property::isWifi);
        addDriverIfNotable(candidates, comps, total, property.isGenerator(), "Backup Generator", Property::isGenerator);
        addDriverIfNotable(candidates, comps, total, property.isPool(), "Swimming Pool", Property::isPool);
        addDriverIfNotable(candidates, comps, total, property.isGym(), "Gym Access", Property::isGym);
        addDriverIfNotable(candidates, comps, total, property.isElevator(), "Elevator Access", Property::isElevator);
        addDriverIfNotable(candidates, comps, total, property.isBalcony(), "Private Balcony", Property::isBalcony);
        addDriverIfNotable(candidates, comps, total, property.isSecurity(), "24/7 Security", Property::isSecurity);
        addDriverIfNotable(candidates, comps, total, property.isSolar(), "Solar Power", Property::isSolar);

        // Rarest positive drivers first, most-common-missing negative
        // drivers first -- the most notable signals surface before the
        // marginal ones, within the top-5 cutoff.
        candidates.sort((a, b) -> {
            boolean aPos = a.getKey().isPositive();
            boolean bPos = b.getKey().isPositive();
            if (aPos != bPos) return aPos ? -1 : 1;
            return aPos ? Double.compare(a.getValue(), b.getValue()) : Double.compare(b.getValue(), a.getValue());
        });

        return candidates.stream().map(Map.Entry::getKey).limit(5).toList();
    }

    private void addDriverIfNotable(List<Map.Entry<ValueDriver, Double>> out, List<Property> comps, int total,
                                     boolean propertyHasAmenity, String label, Predicate<Property> compHasAmenity) {
        long count = comps.stream().filter(compHasAmenity).count();
        double prevalence = (double) count / total;

        if (propertyHasAmenity && prevalence < 0.50) {
            String impact = prevalence < 0.10 ? "+10-15%" : prevalence < 0.25 ? "+5-10%" : "+3-5%";
            out.add(new AbstractMap.SimpleEntry<>(new ValueDriver(label, impact, true), prevalence));
        } else if (!propertyHasAmenity && prevalence > 0.70) {
            out.add(new AbstractMap.SimpleEntry<>(new ValueDriver("Missing " + label, "-5-8%", false), prevalence));
        }
    }
}
