package org.acme.contracts;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/** 测试前清空独立的测试数据库与扫描目录，并关闭定时提醒任务。 */
public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        Path dir = Path.of("target/test-data");
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
        try {
            Files.createDirectories(dir.resolve("scans"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Map.of(
                "data.file", "target/test-data/test.db",
                "scan.dir", "target/test-data/scans",
                "contracts.reminder.cron", "0 0 0 1 1 ? 2099"
        );
    }

    @Override
    public void stop() {
    }
}
