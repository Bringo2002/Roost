package com.roost.util;

/** Shared geo-distance math, used by both GPS listing verification and
 *  nearby-facilities lookup so there's one implementation, not two
 *  copies that could quietly drift apart. */
public final class GeoUtils {

    private GeoUtils() {}

    /** Great-circle distance between two coordinates, in meters. */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusMeters = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }
}
