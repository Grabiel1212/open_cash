package services.navegador.apertura;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
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

class KeyFacilSession {

    static final Gson GSON = new GsonBuilder().create();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final int HOOK_MAX_INTENTOS = 20;
    private static final long HOOK_RETRY_MS = 500;

    static final ExecutorService VENTA_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "VentaProc");
        t.setDaemon(true);
        return t;
    });

    private static final ExecutorService HOOK_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HookInjector");
        t.setDaemon(true);
        return t;
    });

    private final String tabId;
    private final WebSocket ws;
    private final KeyFacilConsoleProcessor processor;
    private final AtomicInteger idGen = new AtomicInteger(0);
    private final Map<Integer, CompletableFuture<Map<String, Object>>> futures = new ConcurrentHashMap<>();
    private final AtomicBoolean alive = new AtomicBoolean(true);
    private final AtomicBoolean inyectando = new AtomicBoolean(false);
    private volatile String sessionId = null;

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

    private class WsListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("🔌 [" + shortId() + "] WS abierto");
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
                    System.err.println("⚠️ [" + shortId() + "] " + e.getMessage());
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
            System.err.println("🔴 [" + shortId() + "] WS error: " + error);
            failAll(error);
        }

        private void failAll(Throwable err) {
            for (CompletableFuture<Map<String, Object>> f : futures.values())
                f.completeExceptionally(err);
            futures.clear();
        }
    }

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

        Object idObj = parsed.get("id");
        if (idObj instanceof Number) {
            int id = ((Number) idObj).intValue();
            CompletableFuture<Map<String, Object>> f = futures.remove(id);
            if (f != null)
                f.complete(parsed);
            return;
        }

        Object methodObj = parsed.get("method");
        if (!(methodObj instanceof String method))
            return;

        if ("Runtime.consoleAPICalled".equals(method)) {
            Object p = parsed.get("params");
            if (p instanceof Map<?, ?> params) {
                processor.handleConsoleEvent((Map<String, Object>) params);
            }
            return;
        }

        if ("Runtime.executionContextCreated".equals(method)) {
            inyectarHookAsync();
        }
    }

    private void loop() {
        try {
            this.sessionId = forzarAdjuntarTarget();
            if (sessionId == null) {
                System.err.println("❌ [" + shortId() + "] attach falló");
                alive.set(false);
                return;
            }

            Map<String, Object> resp = enviar("Runtime.enable", new LinkedHashMap<>(), 3000);
            if (resp.get("error") != null) {
                System.err.println("❌ [" + shortId() + "] Runtime.enable falló");
                alive.set(false);
                return;
            }

            try {
                enviar("Page.enable", new LinkedHashMap<>(), 2000);
                Map<String, Object> sp = new LinkedHashMap<>();
                sp.put("source", KeyFacilScripts.CONSOLE_HOOK);
                enviar("Page.addScriptToEvaluateOnNewDocument", sp, 3000);
            } catch (Exception ignored) {
            }

            System.out.println("✅ [" + shortId() + "] Sesión lista");

        } catch (Exception e) {
            System.err.println("❌ [" + shortId() + "] init: " + e.getMessage());
            alive.set(false);
            return;
        }

        inyectarHookAsync();
        iniciarPollingConsola();
    }

    @SuppressWarnings("unchecked")
    private String forzarAdjuntarTarget() {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("targetId", tabId);
            params.put("flatten", true);
            Map<String, Object> resp = enviar("Target.attachToTarget", params, 3000);
            if (resp.get("error") != null)
                return null;
            Map<String, Object> result = (Map<String, Object>) resp.get("result");
            if (result != null && result.get("sessionId") instanceof String sid)
                return sid;
        } catch (Exception e) {
            System.err.println("❌ [" + shortId() + "] attach ex: " + e.getMessage());
        }
        return null;
    }

    private void inyectarHookAsync() {
        if (!inyectando.compareAndSet(false, true))
            return;
        HOOK_EXECUTOR.submit(() -> {
            try {
                for (int intento = 1; intento <= HOOK_MAX_INTENTOS && alive.get(); intento++) {
                    try {
                        Thread.sleep(HOOK_RETRY_MS);
                    } catch (InterruptedException ie) {
                        return;
                    }
                    if (intentarInyectarHook(intento))
                        return;
                }
                System.err.println("❌ [" + shortId() + "] hook NO instalado");
            } finally {
                inyectando.set(false);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private boolean intentarInyectarHook(int intento) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("expression", KeyFacilScripts.CONSOLE_HOOK);
            params.put("returnByValue", true);
            Map<String, Object> r1 = enviar("Runtime.evaluate", params, 2000);
            if (r1.get("error") != null)
                return false;

            Map<String, Object> check = new LinkedHashMap<>();
            check.put("expression", "Boolean(console.log && console.log.__keyfacilWrapped)");
            check.put("returnByValue", true);
            Map<String, Object> r2 = enviar("Runtime.evaluate", check, 2000);
            if (r2.get("error") != null)
                return false;

            Map<String, Object> result = (Map<String, Object>) r2.get("result");
            if (result == null)
                return false;
            Map<String, Object> inner = (Map<String, Object>) result.get("result");
            if (inner == null)
                return false;

            if (Boolean.TRUE.equals(inner.get("value"))) {
                System.out.println("💉 [" + shortId() + "] Hook ACTIVO ✅");
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void iniciarPollingConsola() {
        Thread t = new Thread(this::loopPolling, "Poll-" + shortId());
        t.setDaemon(true);
        t.start();
        System.out.println("📡 [" + shortId() + "] Polling de ventas iniciado");
    }

    @SuppressWarnings("unchecked")
    private void loopPolling() {
        // 🎯 Solo logs posteriores al arranque de la app.
        // Nunca procesa ventas viejas del buffer, sin importar cuándo reconectes.
        final long base = KeyFacilMonitorService.appStartTime();
        long ultimoT = base;

        while (alive.get() && !Thread.currentThread().isInterrupted()) {
            try {
                String expr = "JSON.stringify((window.__keyfacilLogs||[]).filter(function(e){" +
                        "if (!e || e.t<=" + ultimoT + " || !e.a) return false;" +
                        "return e.a.some(function(x){" +
                        "  if (typeof x!=='string') return false;" +
                        "  return x.toUpperCase().indexOf('PROCESANDO')>=0" +
                        "      || x.indexOf('lista_metodos_pago')>=0;" +
                        "});}))";

                Map<String, Object> params = new LinkedHashMap<>();
                params.put("expression", expr);
                params.put("returnByValue", true);

                Map<String, Object> resp = enviar("Runtime.evaluate", params, 3000);
                if (resp.get("error") != null)
                    continue;

                Map<String, Object> result = (Map<String, Object>) resp.get("result");
                if (result == null)
                    continue;
                Map<String, Object> inner = (Map<String, Object>) result.get("result");
                if (inner == null)
                    continue;

                Object val = inner.get("value");
                if (!(val instanceof String json))
                    continue;
                if (json.isBlank() || "[]".equals(json))
                    continue;

                System.out.println("📡 [" + shortId() + "] Venta detectada");

                java.util.List<Map<String, Object>> logs = KeyFacilMonitorService.GSON.fromJson(json,
                        java.util.List.class);
                for (Map<String, Object> log : logs) {
                    Object tObj = log.get("t");
                    long t = (tObj instanceof Number) ? ((Number) tObj).longValue() : 0;
                    if (t > ultimoT)
                        ultimoT = t;

                    Object arr = log.get("a");
                    if (arr instanceof java.util.List<?> l) {
                        java.util.List<String> strs = new java.util.ArrayList<>();
                        for (Object o : l)
                            strs.add(o != null ? o.toString() : "");
                        if (!strs.isEmpty())
                            processor.procesarDesdePolling(strs);
                    }
                }
            } catch (Exception ignored) {
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                return;
            }
        }
    }

    Map<String, Object> enviar(String method, Map<String, Object> params, int timeoutMs)
            throws Exception {
        if (!alive.get())
            throw new RuntimeException("Session dead");

        int id = idGen.incrementAndGet();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        futures.put(id, future);

        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("id", id);
        cmd.put("method", method);
        cmd.put("params", params);
        if (sessionId != null && !method.startsWith("Target.")) {
            cmd.put("sessionId", sessionId);
        }

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