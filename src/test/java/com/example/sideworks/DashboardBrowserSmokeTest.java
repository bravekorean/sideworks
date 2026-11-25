package com.example.sideworks;

import com.example.sideworks.auth.jwt.JwtTokenProvider;
import com.example.sideworks.user.entity.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.testcontainers.containers.MySQLContainer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in local browser harness. No production settings, database, or credentials are loaded. */
@EnabledIfEnvironmentVariable(named = "SIDEWORKS_DASHBOARD_BROWSER_SMOKE", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=optional:classpath:/dashboard-browser-empty.properties",
        "server.address=127.0.0.1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
        "app.cors.allowed-origins=http://localhost:5173",
        "app.cookie.secure=false",
        "app.attachment.max-file-count=5",
        "app.attachment.max-file-size=10MB",
        "app.attachment.max-total-size=30MB",
        "logging.level.root=WARN",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@Import(DashboardBrowserSmokeTest.Config.class)
class DashboardBrowserSmokeTest {
    private static final Path OUTPUT = Path.of("build/reports/dashboard-browser-smoke").toAbsolutePath();
    private static final Path STOP = OUTPUT.resolve("stop");
    private static final Path FAIL = OUTPUT.resolve("fail-dashboard");
    private static final Path REQUESTS = OUTPUT.resolve("requests.csv");
    private static final String EPHEMERAL_KEY = newEphemeralKey();

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper mapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> EPHEMERAL_KEY);
        registry.add("app.attachment.storage-path", () -> OUTPUT.resolve("attachments").toString());
        registry.add("spring.web.resources.static-locations",
                () -> Path.of("frontend/dist").toAbsolutePath().toUri().toString() + "/");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        @Order(0)
        SecurityFilterChain smokeStaticResources(HttpSecurity http) throws Exception {
            // The deployed frontend is normally served by Nginx; only SPA files are public here.
            return http.securityMatcher("/", "/login", "/dashboard", "/index.html", "/assets/**", "/favicon.ico")
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }

        @Bean
        @ServiceConnection
        MySQLContainer<?> browserMysql() {
            return new MySQLContainer<>("mysql:8.0.44").withUrlParam("rewriteBatchedStatements", "true");
        }

        @Bean
        WebMvcConfigurer smokeSpaRoutes() {
            return new WebMvcConfigurer() {
                @Override
                public void addViewControllers(ViewControllerRegistry registry) {
                    for (String route : List.of("/login", "/dashboard")) {
                        registry.addViewController(route).setViewName("forward:/index.html");
                    }
                }
            };
        }

        @Bean
        FilterRegistrationBean<OncePerRequestFilter> smokeAudit() throws IOException {
            Files.createDirectories(OUTPUT);
            Files.writeString(REQUESTS, "time,path,status,millis\n");
            var filter = new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                FilterChain chain) throws ServletException, IOException {
                    long start = System.nanoTime();
                    try {
                        if (request.getRequestURI().equals("/api/approvals/dashboard") && Files.exists(FAIL)) {
                            response.setStatus(503);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"격리 검증용 대시보드 조회 실패\"}");
                        } else {
                            chain.doFilter(request, response);
                        }
                    } finally {
                        if (request.getRequestURI().startsWith("/api/")) {
                            synchronized (DashboardBrowserSmokeTest.class) {
                                Files.writeString(REQUESTS, String.format(Locale.ROOT, "%s,%s,%d,%.3f%n",
                                                Instant.now(), request.getRequestURI(), response.getStatus(),
                                                (System.nanoTime() - start) / 1_000_000.0), StandardOpenOption.APPEND);
                            }
                        }
                    }
                }
            };
            var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
            registration.setOrder(Ordered.LOWEST_PRECEDENCE);
            return registration;
        }
    }

    @Test
    void serveIsolatedBrowserSmoke() throws Exception {
        assertThat(Files.exists(Path.of("frontend/dist/index.html"))).as("Build the frontend first").isTrue();
        Files.deleteIfExists(STOP);
        Files.deleteIfExists(FAIL);
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            DashboardBenchmarkFixture.seed(jdbc, 1_000);
            // Public disposable fixture password; only its BCrypt hash is written to this new database.
            jdbc.update("update usertbl set user_pass=?", passwordEncoder.encode("dashboard-smoke-only"));
        });
        measureHttp();
        Files.writeString(REQUESTS, "time,path,status,millis\n");
        Files.writeString(OUTPUT.resolve("server.txt"), "http://127.0.0.1:" + port);
        System.out.println("Isolated browser smoke server ready; URL in build/reports/dashboard-browser-smoke/server.txt");
        Instant deadline = Instant.now().plus(Duration.ofMinutes(15));
        while (!Files.exists(STOP) && Instant.now().isBefore(deadline)) Thread.sleep(500);
        assertThat(Files.exists(STOP)).as("Browser verification must finish within 15 minutes").isTrue();
    }

    private void measureHttp() throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            StringBuilder report = new StringBuilder("# Local HTTP approval-data comparison\n\n")
                    .append("1000 documents / 5000 histories; 10 warmup pairs; 50 samples per variant/user.\n")
                    .append("Real JWT/filter/service/MySQL. Baseline: five concurrent HTTP requests; new: one aggregate request.\n")
                    .append("Includes HTTP + JSON parse/assembly; excludes profile, browser rendering and app-shell calls.\n")
                    .append("Single process/run, warm caches, alternating order; not a production result.\n\n")
                    .append("| user | variant | p50 ms | p95 ms |\n|---:|---|---:|---:|\n");
            StringBuilder raw = new StringBuilder("user,sample,variant,nanos\n");
            for (long userId : new long[]{1, 10, 101}) {
                String token = tokens.createAccessToken(userId, "bench-" + userId, UserRole.USER);
                for (int i = 0; i < 10; i++) {
                    assertThat(loadHttp(client, token, false)).isEqualTo(loadHttp(client, token, true));
                }
                long[][] times = new long[2][50];
                for (int i = 0; i < 50; i++) {
                    JsonNode[] responses = new JsonNode[2];
                    for (int variant : i % 2 == 0 ? new int[]{0, 1} : new int[]{1, 0}) {
                        long start = System.nanoTime();
                        responses[variant] = loadHttp(client, token, variant == 1);
                        times[variant][i] = System.nanoTime() - start;
                        raw.append(userId).append(',').append(i).append(',')
                                .append(variant == 0 ? "parallel-old" : "aggregate-new").append(',')
                                .append(times[variant][i]).append('\n');
                    }
                    assertThat(responses[0]).isEqualTo(responses[1]);
                }
                for (int variant = 0; variant < 2; variant++) {
                    Arrays.sort(times[variant]);
                    report.append(String.format(Locale.ROOT, "| %d | %s | %.3f | %.3f |%n", userId,
                            variant == 0 ? "parallel-old" : "aggregate-new",
                            times[variant][24] / 1_000_000.0, times[variant][47] / 1_000_000.0));
                }
            }
            Files.writeString(OUTPUT.resolve("http-results.md"), report);
            Files.writeString(OUTPUT.resolve("http-samples.csv"), raw);
        }
    }

    private JsonNode loadHttp(HttpClient client, String token, boolean aggregate) throws Exception {
        if (aggregate) return parse(client.send(request("/api/approvals/dashboard", token), HttpResponse.BodyHandlers.ofString()));
        List<String> paths = List.of("/api/approvals/pending?page=0&size=4",
                "/api/approvals/sent?page=0&size=1&status=IN_PROGRESS", "/api/approvals/drafts?page=0&size=1",
                "/api/approvals/cc?page=0&size=1", "/api/approvals/activities?page=0&size=5");
        var requests = paths.stream().map(path -> client.sendAsync(request(path, token), HttpResponse.BodyHandlers.ofString())).toList();
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();
        JsonNode pending = parse(requests.get(0).join());
        var summary = mapper.createObjectNode();
        summary.set("pending", pending.get("totalElements"));
        summary.set("inProgress", parse(requests.get(1).join()).get("totalElements"));
        summary.set("drafts", parse(requests.get(2).join()).get("totalElements"));
        summary.set("cc", parse(requests.get(3).join()).get("totalElements"));
        var result = mapper.createObjectNode();
        result.set("summary", summary);
        result.set("pendingApprovals", pending.get("content"));
        result.set("recentActivities", parse(requests.get(4).join()).get("content"));
        return result;
    }

    private HttpRequest request(String path, String token) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token).GET().build();
    }

    private JsonNode parse(HttpResponse<String> response) throws IOException {
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private static String newEphemeralKey() {
        byte[] key = new byte[64];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
