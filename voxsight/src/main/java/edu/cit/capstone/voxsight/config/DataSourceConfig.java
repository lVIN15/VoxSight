package edu.cit.capstone.voxsight.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${spring.datasource.url:}")
    private String springUrl;

    @Value("${spring.datasource.username:}")
    private String springUser;

    @Value("${spring.datasource.password:}")
    private String springPass;

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();

        // 1. If Railway or cloud provider injects DATABASE_URL (postgresql://user:pass@host:port/db)
        if (databaseUrl != null && !databaseUrl.isBlank() && !databaseUrl.startsWith("jdbc:")) {
            try {
                URI uri = new URI(databaseUrl);
                String userInfo = uri.getUserInfo();
                String user = springUser;
                String pass = springPass;
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    user = parts[0];
                    pass = parts[1];
                } else if (userInfo != null) {
                    user = userInfo;
                }

                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();
                if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                    jdbcUrl += "?" + uri.getQuery();
                }

                ds.setDriverClassName("org.postgresql.Driver");
                ds.setJdbcUrl(jdbcUrl);
                ds.setUsername(user);
                ds.setPassword(pass);
                log.info("Initialized DataSource from DATABASE_URL -> {}", jdbcUrl);
                return ds;
            } catch (Exception e) {
                log.warn("Failed to parse DATABASE_URL as URI, fallback to spring.datasource.url: {}", e.getMessage());
            }
        }

        // 2. Standard Spring configuration or local default
        String url = (springUrl != null && !springUrl.isBlank()) ? springUrl : "jdbc:postgresql://localhost:5432/voxsight";

        // Check if local PostgreSQL is reachable; if not, automatically fall back to H2 for local testing
        if (url.contains("localhost:5432") || url.contains("127.0.0.1:5432")) {
            if (!isPortReachable("localhost", 5432)) {
                log.warn("Local PostgreSQL on port 5432 is not reachable. Falling back to in-memory H2 database (PostgreSQL mode) for local testing.");
                ds.setDriverClassName("org.h2.Driver");
                ds.setJdbcUrl("jdbc:h2:mem:voxsight;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
                ds.setUsername("sa");
                ds.setPassword("");
                return ds;
            }
        }

        ds.setDriverClassName("org.postgresql.Driver");
        ds.setJdbcUrl(url);
        ds.setUsername(springUser != null ? springUser : "postgres");
        ds.setPassword(springPass != null ? springPass : "");
        log.info("Initialized DataSource with JDBC URL: {}", url);
        return ds;
    }

    private boolean isPortReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 600);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
