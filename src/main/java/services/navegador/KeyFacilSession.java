package services.navegador;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Sesión CDP contra UNA pestaña de KeyFacil.
 *
 * - Captura por EVENTOS: Runtime.consoleAPICalled (instantáneo, sin polling).
 * - Emulation.setFocusEmulationEnabled periódico (no throttling en background).
 * - El parseo vive en KeyFacilConsoleProcessor.
 */
class KeyFacilSession {

    static final Gson GSON = new GsonBuilder().create();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final long FOCUS_REFRESH_MS = 3000;

    /** Pool compartido para procesar ventas sin bloquear el hilo del WS. */
    static final ExecutorService VENTA_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "VentaProc");
        t.setDaemon(true);
        return t;
    });

    private final String tabId;
    private final WebSocket ws;
    private final KeyFacilConsoleProcessor processor;
    private final AtomicInteger idGen = new AtomicInteger(0);
    private final Map<Integer, CompletableFuture<Map<String, Object>>> futures = new ConcurrentHashMap<>();
    private final AtomicBoolean alive = new AtomicBoolean(true);
    private Thread worker;

    KeyFacilSession(String tabId, String wsUrl, VentaListenerService ventaListener) throws Exception {
        this.tabId = tabId;
        this.processor = new KeyFacilConsoleProcessor(this, ventaListener);
        this.ws = HTTP.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new WsListener())
                .get(5000, TimeUnit.MILLISECONDS);
    }

    boolean isAlive() {
        return alive.get();
    }

    String getTabId() {
        return tabId;
    }

    String shortId() {
        return tabId.substring(0, Math.min(8, tabId.length()));
    }

    void start() {
        worker = new Thread(this::loop, "Cdp-" + shortId());
        worker.setDaemon(true);
        worker.start();
    }

    // =====================================================
    // WebSocket Listener
    // =====================================================
    private class WsListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String msg = buffer.toString();
                buffer.setLength(0);
                try {
                    handleMessage(msg);
                } catch (Exception e) {
                    System.err.println("⚠️ [" + shortId() + "] handleMessage: " + e.getMessage());
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int code, String reason) {
            alive.set(false);
            System.out.println("🔴 [" + shortId() + "] WS cerrado: " + code + " " + reason);
            failAll(new RuntimeException("WS closed"));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            alive.set(false);
            System.out.println("🔴 [" + shortId() + "] WS error: " + error);
            failAll(error);
        }

        private void failAll(Throwable err) {
            for (CompletableFuture<Map<String, Object>> f : futures.values())
                f.completeExceptionally(err);
            futures.clear();
        }
    }

    // =====================================================
    // Despacho de mensajes CDP
    // =====================================================
    @SuppressWarnings("unchecked")
    private void handleMessage(String msg) {
        Map<String, Object> parsed;
        try {
            parsed = GSON.fromJson(msg, Map.class);
        } catch (Exception e) {
            return;
        }
        if (parsed == null)
            return;

        // Respuesta a un comando con id
        Object idObj = parsed.get("id");
        if (idObj instanceof Number) {
            int id = ((Number) idObj).intValue();
            CompletableFuture<Map<String, Object>> f = futures.remove(id);
            if (f != null)
                f.complete(parsed);
            return;
        }

        // Evento CDP
        Object methodObj = parsed.get("method");
        if (!(methodObj instanceof String method))
            return;

        if ("Runtime.consoleAPICalled".equals(method)) {
            Object p = parsed.get("params");
            if (p instanceof Map<?, ?> params) {
                processor.handleConsoleEvent((Map<String, Object>) params);
            }
        }
    }

    // =====================================================
    // Loop: enable + focus emulation periódico
    // =====================================================
    private void loop() {
        try {
            // Captura por eventos: solo necesitamos Runtime.enable
            enviar("Runtime.enable", new LinkedHashMap<>(), 3000);
            System.out.println("✅ [" + shortId() + "] Runtime habilitado (captura por eventos)");
        } catch (Exception e) {
            System.err.println("❌ [" + shortId() + "] fallo al habilitar: " + e.getMessage());
            alive.set(false);
            return;
        }

        long lastFocus = 0;

        while (alive.get() && !Thread.currentThread().isInterrupted()) {
            long now = System.currentTimeMillis();

            if (now - lastFocus > FOCUS_REFRESH_MS) {
                try {
                    Map<String, Object> fp = new LinkedHashMap<>();
                    fp.put("enabled", true);
                    enviar("Emulation.setFocusEmulationEnabled", fp, 2000);
                } catch (Exception ignored) {
                }
                lastFocus = now;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }

        alive.set(false);
        System.out.println("🔴 [" + shortId() + "] worker terminado");
    }

    Map<String, Object> enviar(String metodo, Map<String, Object> params, int timeoutMs)
            throws Exception {
        if (!alive.get())
            throw new RuntimeException("Session dead");

        int id = idGen.incrementAndGet();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        futures.put(id, future);

        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("id", id);
        cmd.put("method", metodo);
        cmd.put("params", params);

        try {
            ws.sendText(GSON.toJson(cmd), true).whenComplete((w, err) -> {
                if (err != null) {
                    alive.set(false);
                    CompletableFuture<Map<String, Object>> f = futures.remove(id);
                    if (f != null)
                        f.completeExceptionally(err);
                }
            });
        } catch (Exception e) {
            alive.set(false);
            futures.remove(id);
            throw e;
        }

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            futures.remove(id);
            throw e;
        }
    }

    // =====================================================
    // Resolver un objeto JS a JSON vía CDP
    // =====================================================
    @SuppressWarnings("unchecked")
    void fetchObjectAsJson(String objectId, java.util.function.Consumer<String> callback) {
        VENTA_EXECUTOR.submit(() -> {
            try {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("objectId", objectId);
                params.put("functionDeclaration",
                        "function(){ try { return JSON.stringify(this); } catch(e) { return null; } }");
                params.put("returnByValue", true);

                Map<String, Object> resp = enviar("Runtime.callFunctionOn", params, 3000);

                Map<String, Object> result = (Map<String, Object>) resp.get("result");
                if (result == null) {
                    callback.accept(null);
                    return;
                }

                Map<String, Object> inner = (Map<String, Object>) result.get("result");
                if (inner == null) {
                    callback.accept(null);
                    return;
                }

                Object value = inner.get("value");
                callback.accept(value != null ? value.toString() : null);

            } catch (Exception e) {
                callback.accept(null);
            }
        });
    }

    void close() {
        alive.set(false);
        if (worker != null)
            worker.interrupt();
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        } catch (Exception ignored) {
        }
    }
}