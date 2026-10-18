package helpers;

import net.datafaker.Faker;

import java.util.UUID;

/**
 * Throwaway test identities. Username fits backend bounds 3–64 (credentials.go).
 */
public final class DataFaker {

    private static final Faker FAKER = new Faker();

    private DataFaker() {
    }

    public static String username() {
        return username(FAKER);
    }

    /** Test seam: length guards are exercised with a scripted faker. */
    public static String username(Faker faker) {
        String base = faker.credentials().username().replaceAll("[^A-Za-z0-9]", "");
        if (base.length() < 3) {
            base = "user" + base;
        }
        if (base.length() > 48) {
            base = base.substring(0, 48);
        }
        return base + "_" + faker.number().digits(6);
    }

    /** Meets backend password min 6 (credentials.go). */
    public static String password() {
        return FAKER.credentials().password(8, 16);
    }

    /** Exactly min 3 — unique hex slice. */
    public static String usernameAtMinLength() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 3);
    }

    /** Exactly min 6. */
    public static String passwordAtMinLength() {
        return "123456";
    }
}
