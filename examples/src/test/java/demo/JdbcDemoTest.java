package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import javapi.testkit.TestClient;
import javapi.testkit.TestResponse;

class JdbcDemoTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(TestResponse response) {
        return (Map<String, Object>) response.json();
    }

    @Test
    void jdbcCrudAndTransactionalTransferOverHttp() throws Exception {
        String dbUrl = "jdbc:h2:mem:demotest;DB_CLOSE_DELAY=-1";
        App.initSchema(dbUrl);
        try (TestClient client = TestClient.forApp(App.configure(dbUrl))) {
            TestResponse created = client.post("/db/items",
                    new CreateItem("alpha", 10, "supplier@example.com"));
            assertEquals(200, created.status(), created.text());
            int fromId = ((Number) map(created).get("id")).intValue();

            TestResponse createdTwo = client.post("/db/items",
                    new CreateItem("beta", 5, null));
            assertEquals(200, createdTwo.status(), createdTwo.text());
            int toId = ((Number) map(createdTwo).get("id")).intValue();

            TestResponse item = client.get("/db/items/" + fromId);
            assertEquals(200, item.status(), item.text());
            assertTrue(item.text().contains("alpha"), item.text());
            assertTrue(item.text().contains("supplier@example.com"), item.text());

            TestResponse transfer = client.post("/db/transfer?from=" + fromId
                    + "&to=" + toId + "&amount=3");
            assertEquals(200, transfer.status(), transfer.text());

            assertEquals(7, ((Number) map(client.get("/db/items/" + fromId)).get("quantity")).intValue());
            assertEquals(8, ((Number) map(client.get("/db/items/" + toId)).get("quantity")).intValue());
        }
    }

    @Test
    void transactionRollsBackTransferWhenSourceMissing() throws Exception {
        String dbUrl = "jdbc:h2:mem:demotest2;DB_CLOSE_DELAY=-1";
        App.initSchema(dbUrl);
        try (TestClient client = TestClient.forApp(App.configure(dbUrl))) {
            TestResponse created = client.post("/db/items",
                    new CreateItem("only", 10, null));
            int id = ((Number) map(created).get("id")).intValue();

            TestResponse transfer = client.post("/db/transfer?from=" + id
                    + "&to=999&amount=3");
            assertEquals(404, transfer.status(), transfer.text());

            assertEquals(10, ((Number) map(client.get("/db/items/" + id)).get("quantity")).intValue(),
                    "quantity must be unchanged after a rolled-back transaction");
        }
    }
}
