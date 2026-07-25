package com.smartjmeter.splunk;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SplunkSearchClientTest {

    @Test
    void buildsCorrelationSpl() {
        String spl = SplunkSearchClient.buildCorrelationSpl("app", 1_700_000_000L, 1_700_000_060L);
        assertEquals(
                "search index=app (error OR timeout OR exception) earliest=1700000000 latest=1700000060",
                spl);
    }

    @Test
    void parsesResultsJson() throws Exception {
        String body = """
                {
                  "preview": false,
                  "results": [
                    {"_time":"2026-01-01T00:00:00","host":"h1","source":"app.log","_raw":"error 500"},
                    {"_time":"2026-01-01T00:00:05","host":"h2","source":"app.log","_raw":"timeout"}
                  ]
                }
                """;
        List<Map<String, Object>> rows = SplunkSearchClient.parseResults(body);
        assertEquals(2, rows.size());
        assertEquals("h1", rows.get(0).get("host"));
        assertEquals("timeout", rows.get(1).get("_raw"));
    }

    @Test
    void parsesEmptyBody() throws Exception {
        assertTrue(SplunkSearchClient.parseResults("{}").isEmpty());
        assertTrue(SplunkSearchClient.parseResults("{\"results\":[]}").isEmpty());
    }

    @Test
    void runSearchWithoutConfigReturnsEmpty() {
        SplunkSearchClient client = new SplunkSearchClient("", "");
        List<Map<String, Object>> rows = client.runSearch("search index=app");
        assertNotNull(rows);
        assertTrue(rows.isEmpty());
    }
}
