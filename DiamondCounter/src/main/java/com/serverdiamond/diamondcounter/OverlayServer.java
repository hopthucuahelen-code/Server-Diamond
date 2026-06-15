package com.serverdiamond.diamondcounter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Tiny embedded web server that serves the transparent overlay page and pushes
 * real-time state updates to the browser via Server-Sent Events (SSE).
 * No external dependencies are required.
 */
public class OverlayServer {

    private final int port;
    private final String html;
    private final Supplier<String> stateProvider;
    private final Logger logger;

    private HttpServer server;
    private final List<HttpExchange> clients = new CopyOnWriteArrayList<>();

    public OverlayServer(int port, String html, Supplier<String> stateProvider, Logger logger) {
        this.port = port;
        this.html = html;
        this.stateProvider = stateProvider;
        this.logger = logger;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/events")) {
                handleEvents(exchange);
            } else if (path.equals("/") || path.equals("/index.html")) {
                byte[] body = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            }
        });

        server.start();
        logger.info("[DiamondCounter] Overlay available at http://localhost:" + port);
    }

    private void handleEvents(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, 0);

        clients.add(exchange);
        // Send the current state immediately so a freshly opened page is correct.
        writeTo(exchange, stateProvider.get());
    }

    /** Push a JSON payload to every connected overlay. Safe to call from the main thread. */
    public void broadcast(String json) {
        for (HttpExchange client : clients) {
            if (!writeTo(client, json)) {
                clients.remove(client);
                try { client.close(); } catch (Exception ignored) {}
            }
        }
    }

    private boolean writeTo(HttpExchange exchange, String json) {
        try {
            byte[] data = ("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8);
            OutputStream os = exchange.getResponseBody();
            os.write(data);
            os.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void stop() {
        for (HttpExchange client : clients) {
            try { client.close(); } catch (Exception ignored) {}
        }
        clients.clear();
        if (server != null) {
            server.stop(0);
        }
    }
}
