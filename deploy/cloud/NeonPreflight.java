import java.sql.DriverManager;
import java.util.Properties;
import org.flywaydb.core.Flyway;

// Read-only preflight with the same JDBC/Flyway dependencies as the backend.
// No HTTP server or application scheduler starts during this check.
class NeonPreflight {
    public static void main(String[] args) {
        try {
            String url = System.getenv("DB_URL");
            if (url == null || !url.contains("sslmode=verify-full")
                    || !url.contains("org.postgresql.ssl.DefaultJavaSSLFactory")) {
                throw new IllegalStateException("Verified TLS configuration required");
            }
            url += "&options=-c%20default_transaction_read_only%3Don";
            var properties = new Properties();
            properties.setProperty("user", System.getenv("DB_USER"));
            properties.setProperty("password", System.getenv("DB_PASSWORD"));
            properties.setProperty("connectTimeout", "15");
            properties.setProperty("socketTimeout", "30");
            try (var connection = DriverManager.getConnection(url, properties);
                 var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("SELECT current_user='medifind_app', current_setting('default_transaction_read_only')='on', (SELECT count(*) FROM medicamento)=13, (SELECT count(*) FROM farmacia)=5, (SELECT count(*) FROM reserva)=29")) {
                    if (!result.next()) throw new IllegalStateException("No query result");
                    for (int i = 1; i <= 5; i++) {
                        if (!result.getBoolean(i)) throw new IllegalStateException("Unexpected role, mode or data");
                    }
                }
            }
            var validation = Flyway.configure()
                    .dataSource(url, properties.getProperty("user"), properties.getProperty("password"))
                    .locations("filesystem:backend/src/main/resources/db/migration",
                            "filesystem:deploy/cloud/backend/main/resources/db/cloud")
                    .baselineOnMigrate(false)
                    .load().validateWithResult();
            if (!validation.validationSuccessful) throw new IllegalStateException("Flyway validation failed");
            System.out.println("NEON_PREFLIGHT_OK: JDBC TLS verify-full; application role; read-only; data counts; Flyway checksums");
        } catch (Exception error) {
            // Connection exceptions may contain connection details. Do not echo them.
            System.err.println("NEON_PREFLIGHT_FAILED: " + error.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
