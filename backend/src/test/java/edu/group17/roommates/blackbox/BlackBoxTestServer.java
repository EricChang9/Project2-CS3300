package edu.group17.roommates.blackbox;

import edu.group17.roommates.RoommateExpensesApplication;
import edu.group17.roommates.support.TestIdentityConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

/** Test-classpath-only identity boundary; never packaged with the production app. */
public final class BlackBoxTestServer {
    public static void main(String[] args) throws Exception {
        String run = System.getenv("BB_RUN_ID");
        String project = System.getenv("BB_PROJECT_ID");
        String emulator = System.getenv("FIRESTORE_EMULATOR_HOST");
        if (run == null || !run.matches("[a-zA-Z0-9_-]+") || project == null
                || !project.startsWith("demo-bb-") || emulator == null || emulator.isBlank()) {
            throw new IllegalArgumentException("An isolated demo-bb- project and explicit emulator are required");
        }
        StringBuilder json = new StringBuilder("{");
        for (String role : new String[]{"alice", "bob", "charlie", "outsider"}) {
            if (json.length() > 1) json.append(',');
            String uid = run + "-" + role;
            String name = Character.toUpperCase(role.charAt(0)) + role.substring(1);
            String token = TestIdentityConfiguration.token(uid, Map.of("name", name, "email", role+"@example.test"), false);
            json.append('"').append(role).append("\":{\"uid\":\"").append(uid)
                    .append("\",\"email\":\"").append(role).append("@example.test\",\"token\":\"")
                    .append(token).append("\"}");
        }
        var file = Path.of(System.getenv("BB_IDENTITIES_FILE"));
        Files.writeString(file, json.append('}').toString());
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        new SpringApplicationBuilder(RoommateExpensesApplication.class, TestIdentityConfiguration.class).run(
                "--server.port="+System.getenv().getOrDefault("BB_API_PORT", "18080"),
                "--app.firestore.enabled=true", "--app.firestore.project-id="+project,
                "--app.firestore.emulator-host="+emulator,
                "--app.auth.project-id="+TestIdentityConfiguration.PROJECT);
    }
}
