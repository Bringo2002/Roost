package com.roost.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the V11 migration (major roads) on a brand-new database and checks
 * the rules the database itself enforces on road segments, plus the
 * bounding-box query the nearest-road lookup will rely on.
 */
@Testcontainers
class MajorRoadMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static final String COLUMNS = "country_code, osm_way_id, name, ref, road_class, "
            + "start_lat, start_lng, end_lat, end_lng, min_lat, max_lat, min_lng, max_lng";

    private Connection migratedDatabase(String name) throws SQLException {
        try (Connection admin = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        String url = postgres.getJdbcUrl().replaceFirst("/[^/?]+(?=\\?|$)", "/" + name);
        Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
        return DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
    }

    /** A short stretch of Thika Road near Muthaiga; individual tests change what they are testing. */
    private static String segment(String country, String name, String ref, String roadClass,
                                  double minLat, double maxLat, double minLng, double maxLng) {
        return String.format(java.util.Locale.ROOT,
                "INSERT INTO major_road_segment (%s) VALUES (%s, 1, %s, %s, '%s', %f, %f, %f, %f, %f, %f, %f, %f)",
                COLUMNS, quote(country), quote(name), quote(ref), roadClass,
                minLat, minLng, maxLat, maxLng, minLat, maxLat, minLng, maxLng);
    }

    private static String quote(String value) {
        return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    }

    private static String thikaRoad() {
        return segment("KE", "Thika Road", "A2", "trunk", -1.2300, -1.2290, 36.8600, 36.8610);
    }

    @Test
    @DisplayName("a named road segment is accepted")
    void namedSegmentIsAccepted() throws Exception {
        try (Connection connection = migratedDatabase("road_ok");
             Statement statement = connection.createStatement()) {
            statement.execute(thikaRoad());

            try (ResultSet rs = statement.executeQuery("SELECT count(*) FROM major_road_segment")) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    @DisplayName("a road with only a reference number is accepted")
    void referenceOnlySegmentIsAccepted() throws Exception {
        try (Connection connection = migratedDatabase("road_ref");
             Statement statement = connection.createStatement()) {
            statement.execute(segment("KE", null, "A104", "primary", -1.30, -1.29, 36.70, 36.71));
        }
    }

    @Test
    @DisplayName("a road with neither a name nor a reference is refused")
    void unlabelledSegmentIsRefused() throws Exception {
        try (Connection connection = migratedDatabase("road_unlabelled");
             Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.execute(
                    segment("KE", null, null, "trunk", -1.30, -1.29, 36.70, 36.71)));
            assertThrows(SQLException.class, () -> statement.execute(
                    segment("KE", "  ", "", "trunk", -1.30, -1.29, 36.70, 36.71)));
        }
    }

    @Test
    @DisplayName("an unknown road class is refused")
    void unknownClassIsRefused() throws Exception {
        try (Connection connection = migratedDatabase("road_class");
             Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.execute(
                    segment("KE", "Tchui Road", null, "residential", -1.30, -1.29, 36.70, 36.71)));
        }
    }

    @Test
    @DisplayName("the country must be a two-letter upper-case code")
    void badCountryIsRefused() throws Exception {
        try (Connection connection = migratedDatabase("road_country");
             Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.execute(
                    segment("ke", "Thika Road", "A2", "trunk", -1.30, -1.29, 36.70, 36.71)));
            assertThrows(SQLException.class, () -> statement.execute(
                    segment("KEN", "Thika Road", "A2", "trunk", -1.30, -1.29, 36.70, 36.71)));
        }
    }

    @Test
    @DisplayName("a bounding box with its edges the wrong way round is refused")
    void backwardsBoxIsRefused() throws Exception {
        try (Connection connection = migratedDatabase("road_box");
             Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.execute(
                    segment("KE", "Thika Road", "A2", "trunk", -1.29, -1.30, 36.70, 36.71)));
        }
    }

    @Test
    @DisplayName("coordinates outside the globe are refused")
    void outOfRangeCoordinatesAreRefused() throws Exception {
        try (Connection connection = migratedDatabase("road_range");
             Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.execute(
                    segment("KE", "Thika Road", "A2", "trunk", -91.0, -1.29, 36.70, 36.71)));
            assertThrows(SQLException.class, () -> statement.execute(
                    segment("KE", "Thika Road", "A2", "trunk", -1.30, -1.29, 36.70, 181.0)));
        }
    }

    @Test
    @DisplayName("the bounding-box query returns only the segments near a point")
    void boundingBoxQueryFindsOnlyNearbySegments() throws Exception {
        try (Connection connection = migratedDatabase("road_box_query");
             Statement statement = connection.createStatement()) {
            statement.execute(thikaRoad());
            // Mombasa Road, far to the south-east, and a road in another country at the same spot.
            statement.execute(segment("KE", "Mombasa Road", "A109", "trunk", -1.35, -1.34, 36.90, 36.91));
            statement.execute(segment("UG", "Jinja Road", null, "primary", -1.2300, -1.2290, 36.8600, 36.8610));

            try (ResultSet rs = statement.executeQuery(
                    "SELECT name FROM major_road_segment WHERE country_code = 'KE' "
                            + "AND min_lat <= -1.2200 AND max_lat >= -1.2400 "
                            + "AND min_lng <= 36.8700 AND max_lng >= 36.8500")) {
                assertTrue(rs.next());
                assertEquals("Thika Road", rs.getString(1));
                assertFalse(rs.next(), "only the segment inside the box, in that country");
            }
        }
    }

    @Test
    @DisplayName("each country has at most one import record")
    void oneImportRecordPerCountry() throws Exception {
        try (Connection connection = migratedDatabase("road_import");
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO major_road_import (country_code, source, road_classes, segment_count) "
                    + "VALUES ('KE', 'kenya-latest.osm.pbf', 'motorway,trunk,primary', 1200)");

            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO major_road_import (country_code, source, road_classes, segment_count) "
                            + "VALUES ('KE', 'kenya-latest.osm.pbf', 'motorway,trunk,primary', 1300)"));
            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO major_road_import (country_code, source, road_classes, segment_count) "
                            + "VALUES ('UG', 'uganda-latest.osm.pbf', 'trunk', -1)"));
        }
    }
}
