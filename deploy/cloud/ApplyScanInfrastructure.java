import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

/** Standard Flyway migration, limited to the one additive cloud infrastructure migration. No data restore. */
class ApplyScanInfrastructure {
    public static void main(String[] args) {
        try {
            String url=System.getenv("DB_URL"),user=System.getenv("DB_USER"),password=System.getenv("DB_PASSWORD");
            if(!"neondb_owner".equals(user)||url==null||!url.contains("sslmode=verify-full"))throw new IllegalStateException();
            var flyway=Flyway.configure().dataSource(url,user,password)
                .locations("filesystem:backend/src/main/resources/db/migration","filesystem:deploy/cloud/backend/main/resources/db/cloud")
                .target(MigrationVersion.fromVersion("15")).ignoreMigrationPatterns("*:pending").baselineOnMigrate(false).load();
            flyway.validate();
            var pending=flyway.info().pending();
            if(pending.length>1||(pending.length==1&&!"15".equals(pending[0].getVersion().getVersion())))throw new IllegalStateException();
            String current=flyway.info().current().getVersion().getVersion();
            if(!Set.of("14","15").contains(current))throw new IllegalStateException();
            int migrated=flyway.migrate().migrationsExecuted;
            if(migrated>1)throw new IllegalStateException();
            try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {
                sql.execute("GRANT SELECT,UPDATE ON cloud_scan_lease TO medifind_app");
                try(var r=sql.executeQuery("SELECT NOT has_schema_privilege('medifind_app','public','CREATE'),(SELECT count(*) FROM cloud_scan_lease)=1,(SELECT owner IS NULL AND expires_at IS NULL FROM cloud_scan_lease)")) {
                    r.next();for(int i=1;i<=3;i++)if(!r.getBoolean(i))throw new IllegalStateException();
                }
            }
            flyway.validate();
            System.out.println("SCAN_INFRASTRUCTURE_OK:"+migrated);
        } catch(Exception ex) {System.err.println("SCAN_INFRASTRUCTURE_FAILED:"+ex.getClass().getSimpleName());System.exit(1);}
    }
}
