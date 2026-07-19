package com.srm.creditengine.cucumber;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Context-owned HTTP provider used to exercise the production RestClient adapter. */
final class AcceptanceFxProviderStub implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;
    private final Queue<ScriptedResponse> responses = new ConcurrentLinkedQueue<>();
    private final AtomicInteger requests = new AtomicInteger();

    private AcceptanceFxProviderStub(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
        server.createContext("/api/v1/rates/", this::respond);
        server.setExecutor(executor);
        server.start();
    }

    static AcceptanceFxProviderStub start(int port) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "cucumber-fx-provider");
                thread.setDaemon(true);
                return thread;
            });
            return new AcceptanceFxProviderStub(server, executor);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start acceptance FX provider", exception);
        }
    }

    void reset() {
        responses.clear();
        requests.set(0);
    }

    void enqueue(int status, String body) {
        responses.add(new ScriptedResponse(status, body == null ? "" : body));
    }

    int requestCount() {
        return requests.get();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respond(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        ScriptedResponse response = responses.poll();
        if (response == null) {
            response = new ScriptedResponse(503, "{\"error\":\"no scripted response\"}");
        }
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private record ScriptedResponse(int status, String body) {}
}
