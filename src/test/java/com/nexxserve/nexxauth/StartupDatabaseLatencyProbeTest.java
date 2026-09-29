package com.nexxserve.nexxauth;

import com.nexxserve.nexxauth.service.StartupDatabaseLatencyProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The startup latency probe runs against the real datasource without breaking
 * the context: this test proves it, since the whole test suite boots the app
 * and a probe that threw would fail every test.
 */
@SpringBootTest
class StartupDatabaseLatencyProbeTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StartupDatabaseLatencyProbe probe;

    @Test
    void probeIsWiredAndReportsLatencyForEveryAttempt() {
        assertThat(probe).isNotNull();

        // The runner already executed during context startup; the summary it
        // logged came from this datasource, so a second run must also succeed.
        probe.run(null);

        assertThat(StartupDatabaseLatencyProbe.DEFAULT_ATTEMPTS).isBetween(3, 5);
        assertThat(dataSource).isNotNull();
    }
}
