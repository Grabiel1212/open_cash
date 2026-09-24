package services.navegador.impresion;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import helpers.MensajeHelper;
import services.cash.ConfigManagerDB;

/**
 * Servicio INDEPENDIENTE de impresión automática en KeyFácil.
 *
 * - Levanta Chrome con --kiosk-printing en el puerto CDP 9223.
 * - Perfil propio en C:\ChromePrintKeyFacil (no choca con el del monitor).
 * - Abre /#/ventas/nuevo y clickea el botón IMPRIMIR automáticamente.
 * - Usa la impresora configurada en Home (default de Windows).
 */
public class ChromePrintService {

    private static final int DEBUG_PORT = 9223;
    private static final String USER_DATA_DIR = "C:\\ChromePrintKeyFacil";
    private static final String KEYFACIL_HOST = "keyfacil-erp.vitekey.com";
    private static final String URL_NUEVA_VENTA = "https://" + KEYFACIL_HOST + "/#/ventas/nuevo";

    private static final long REINJECT_MS = 20_000;

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().create();

    private final ConfigManagerDB configManager;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger idGen = new AtomicInteger(0);
    private final Map<Integer, CompletableFuture<Map<String, Object>>> futures = new ConcurrentHashMap<>();

    private Thread worker;
    private volatile WebSocket ws;
    private volatile boolean wsAlive = false;
    private volatile String tabId;
    private volatile long lastInject = 0;

    public ChromePrintService(ConfigManagerDB configManager) {
        this.configManager = configManager;
    }

    public boolean isRunning() {
        return running.get();
    }

    // =====================================================
    // CICLO DE VIDA
    // =====================================================
    public void start() {
        if (!running.compareAndSet(false, true))
            return;
        worker = new Thread(this::bucle, "ChromePrintService");
        worker.setDaemon(true);
        worker.start();
        MensajeHelper.info("🖨️ ChromePrintService iniciado");
    }

    public void stop() {
        if (!running.compareAndSet(true, false))
            return;
        if (worker != null)
            worker.interrupt();
        cerrarWs();
        MensajeHelper.info("🛑 ChromePrintService detenido");
    }

    public void abrirNavegador() {
        new Thread(() -> {
            try {
                aplicarImpresoraComoDefault();
                if (asegurarChrome()) {
                    asegurarPestanaVentaNueva();
                }
            } catch (Exception e) {
                MensajeHelper.error("No se pudo abrir Chrome (print): " + e.getMessage(), e);
            }
        }, "AbrirChromePrint").start();
    }

    // =====================================================
    // BUCLE PRINCIPAL
    // =====================================================
    private void bucle() {
        try {
            aplicarImpresoraComoDefault();

            if (!asegurarChrome()) {
                MensajeHelper.error("Chrome (print) no abrió el puerto " + DEBUG_PORT, null);
                running.set(false);
                return;
            }

            System.out.println("🖨️ ChromePrintService: Chrome listo en puerto " + DEBUG_PORT);
            System.out.println("👉 Esperando botón IMPRIMIR en " + URL_NUEVA_VENTA);

            while (running.get()) {
                try {
                    asegurarPestanaVentaNueva();

                    if (tabId != null && !wsAlive) {
                        conectar();
                    }

                    long now = System.currentTimeMillis();
                    if (wsAlive && now - lastInject > REINJECT_MS) {
                        inyectarDetector();
                        lastInject = now;
                    }

                    Thread.sleep(1000);

                } catch (InterruptedException ie) {
                    break;
                } catch (Exception e) {
                    System.err.println("⚠️ ChromePrintService: " + e.getMessage());
                    cerrarWs();
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }

        } catch (InterruptedException ie) {
            // salida limpia
        } catch (Exception e) {
            MensajeHelper.error("Error en ChromePrintService: " + e.getMessage(), e);
        } finally {
            running.set(false);
            cerrarWs();
        }
    }

    // =====================================================
    // IMPRESORA DEFAULT
    // =====================================================
    private void aplicarImpresoraComoDefault() {
        if (configManager == null)
            return;

        String impresora = null;
        try {
            impresora = configManager.getImpresoraSeleccionada();
        } catch (Exception ignored) {
        }

        if (impresora == null || impresora.isBlank()) {
            System.out.println("⚠️ ChromePrintService: no hay impresora seleccionada en Home.");
            return;
        }

        try {
            String nombreEscapado = impresora.replace("'", "''");
            String ps = "(New-Object -ComObject WScript.Network).SetDefaultPrinter('"
                    + nombreEscapado + "')";

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-Command", ps);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(5, TimeUnit.SECONDS);

            System.out.println("🖨️ Impresora default → " + impresora);
        } catch (Exception e) {
            System.err.println("⚠️ No se pudo setear la impresora default: " + e.getMessage());
        }
    }

    // =====================================================
    // CHROME
    // =====================================================
    private boolean asegurarChrome() throws Exception {
        if (chromeDebugDisponible())
            return true;

        System.out.println("🟢 Iniciando Chrome (print) en puerto " + DEBUG_PORT + "...");
        lanzarChrome();

        if (esperarDebugDisponible(8))
            return true;

        System.out.println("⚠️ Puerto " + DEBUG_PORT + " no responde. Matando Chrome zombie (print)...");
        matarChromeConNuestroPerfil();
        Thread.sleep(1500);

        System.out.println("🔄 Relanzando Chrome (print)...");
        lanzarChrome();
        return esperarDebugDisponible(15);
    }

    private void lanzarChrome() throws Exception {
        ProcessBuilder chrome = new ProcessBuilder(
                obtenerRutaChrome(),
                "--remote-debugging-port=" + DEBUG_PORT,
                "--user-data-dir=" + USER_DATA_DIR,
                "--kiosk-printing",
                "--start-maximized",
                URL_NUEVA_VENTA);
        chrome.start();
    }

    private boolean esperarDebugDisponible(int segundos) throws InterruptedException {
        for (int i = 0; i < segundos * 2; i++) {
            if (chromeDebugDisponible())
                return true;
            Thread.sleep(500);
        }
        return chromeDebugDisponible();
    }

    private void matarChromeConNuestroPerfil() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-Command",
                    "Get-CimInstance Win32_Process -Filter \"name='chrome.exe'\" | " +
                            "Where-Object { $_.CommandLine -like '*ChromePrintKeyFacil*' } | " +
                            "ForEach-Object { try { Stop-Process -Id $_.ProcessId -Force } catch {} }");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Error matando Chrome zombie (print): " + e.getMessage());
        }
    }

    private boolean chromeDebugDisponible() {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create("http://127.0.0.1:" + DEBUG_PORT + "/json/version")
                    .toURL().openConnection();
            c.setConnectTimeout(500);
            c.setReadTimeout(500);
            c.connect();
            int code = c.getResponseCode();
            c.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String obtenerRutaChrome() {
        String[] rutas = {
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe"
        };
        for (String r : rutas)
            if (new File(r).exists())
                return r;
        throw new RuntimeException("No se encontró Google Chrome.");
    }

    // =====================================================
    // PESTAÑA
    // =====================================================
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> obtenerTargets() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + DEBUG_PORT + "/json")).build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return GSON.fromJson(resp.body(), List.class);
    }

    private void asegurarPestanaVentaNueva() throws Exception {
        List<Map<String, Object>> targets = obtenerTargets();

        String firstKeyFacilTab = null;
        boolean tabIdVive = false;

        for (Map<String, Object> t : targets) {
            Object type = t.get("type");
            Object url = t.get("url");
            Object id = t.get("id");
            if (type == null || url == null || id == null)
                continue;
            if (!"page".equals(type.toString()))
                continue;
            if (!url.toString().contains(KEYFACIL_HOST))
                continue;

            String idStr = id.toString();
            if (firstKeyFacilTab == null)
                firstKeyFacilTab = idStr;
            if (idStr.equals(tabId))
                tabIdVive = true;
        }

        if (!tabIdVive) {
            if (firstKeyFacilTab != null) {
                tabId = firstKeyFacilTab;
                cerrarWs();
            } else {
                crearPestana(URL_NUEVA_VENTA);
                Thread.sleep(1500);
            }
        }
    }

    private void crearPestana(String url) throws Exception {
        final WebSocket[] wsRef = new WebSocket[1];
        final CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();

        String browserWs = obtenerBrowserWsUrl();

        wsRef[0] = HTTP.newWebSocketBuilder()
                .buildAsync(URI.create(browserWs), new WebSocket.Listener() {
                    final StringBuilder buf = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket w) {
                        w.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
                        buf.append(data);
                        if (last) {
                            String msg = buf.toString();
                            buf.setLength(0);
                            try {
                                Map<String, Object> p = GSON.fromJson(msg, Map.class);
                                Object idObj = p.get("id");
                                if (idObj instanceof Number && ((Number) idObj).intValue() == 1) {
                                    future.complete(p);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        w.request(1);
                        return null;
                    }
                })
                .get(5000, TimeUnit.MILLISECONDS);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("url", url);

        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("id", 1);
        cmd.put("method", "Target.createTarget");
        cmd.put("params", params);

        wsRef[0].sendText(GSON.toJson(cmd), true);
        try {
            future.get(5000, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
        try {
            wsRef[0].sendClose(WebSocket.NORMAL_CLOSURE, "done");
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private String obtenerBrowserWsUrl() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + DEBUG_PORT + "/json/version")).build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> info = GSON.fromJson(resp.body(), Map.class);
        Object ws = info.get("webSocketDebuggerUrl");
        if (ws == null)
            throw new RuntimeException("Sin WebSocket del navegador");
        return ws.toString();
    }

    // =====================================================
    // WEBSOCKET CDP
    // =====================================================
    @SuppressWarnings("unchecked")
    private void conectar() throws Exception {
        if (tabId == null)
            return;

        String wsUrl = null;
        for (Map<String, Object> t : obtenerTargets()) {
            Object id = t.get("id");
            if (id != null && id.toString().equals(tabId)) {
                Object ws = t.get("webSocketDebuggerUrl");
                if (ws != null)
                    wsUrl = ws.toString();
                break;
            }
        }
        if (wsUrl == null) {
            System.err.println("⚠️ No hay WebSocket para la pestaña " + tabId);
            return;
        }

        idGen.set(0);
        futures.clear();
        lastInject = 0;

        WebSocket socket = HTTP.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new WsListener())
                .get(5000, TimeUnit.MILLISECONDS);

        this.ws = socket;
        this.wsAlive = true;

        enviar("Runtime.enable", new LinkedHashMap<>(), 3000);

        System.out.println("✅ ChromePrintService conectado a " + tabId);
    }

    private void cerrarWs() {
        wsAlive = false;
        WebSocket w = ws;
        ws = null;
        if (w != null) {
            try {
                w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {
            }
        }
        futures.clear();
    }

    private class WsListener implements WebSocket.Listener {
        private final StringBuilder buf = new StringBuilder();

        @Override
        public void onOpen(WebSocket w) {
            w.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                String msg = buf.toString();
                buf.setLength(0);
                try {
                    handleMessage(msg);
                } catch (Exception ignored) {
                }
            }
            w.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
            wsAlive = false;
            System.out.println("🔴 ChromePrintService WS cerrado: " + code);
            return null;
        }

        @Override
        public void onError(WebSocket w, Throwable error) {
            wsAlive = false;
            System.err.println("🔴 ChromePrintService WS error: " + error);
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
                Object args = params.get("args");
                if (args instanceof List<?> list) {
                    for (Object a : list) {
                        if (a instanceof Map<?, ?> m) {
                            Object v = m.get("value");
                            if (v instanceof String s && s.startsWith("KF_PRINT_")) {
                                System.out.println("🖨️ " + s);
                            }
                        }
                    }
                }
            }
        }
    }

    private Map<String, Object> enviar(String metodo, Map<String, Object> params, int timeoutMs)
            throws Exception {
        if (!wsAlive || ws == null)
            throw new RuntimeException("WS no conectado");

        int id = idGen.incrementAndGet();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        futures.put(id, future);

        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("id", id);
        cmd.put("method", metodo);
        cmd.put("params", params);

        ws.sendText(GSON.toJson(cmd), true).whenComplete((w, err) -> {
            if (err != null) {
                wsAlive = false;
                CompletableFuture<Map<String, Object>> f = futures.remove(id);
                if (f != null)
                    f.completeExceptionally(err);
            }
        });

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            futures.remove(id);
            throw e;
        }
    }

    // =====================================================
    // DETECTOR JS
    // =====================================================
    private void inyectarDetector() {
        String script = """
                (function() {
                    var HOST_OK = 'keyfacil-erp.vitekey.com';
                    var HASH_OK = '/ventas/nuevo';
                    var MARK    = 'data-kf-print-clicked';

                    if (window.__kfPrintObserver) {
                        try { window.__kfPrintObserver.disconnect(); } catch (e) {}
                        window.__kfPrintObserver = null;
                    }
                    if (window.__kfPrintHashHandler) {
                        try { window.removeEventListener('hashchange', window.__kfPrintHashHandler); } catch (e) {}
                        window.__kfPrintHashHandler = null;
                    }

                    function enUrlCorrecta() {
                        return location.hostname === HOST_OK
                            && location.hash.indexOf(HASH_OK) !== -1;
                    }

                    function intentarImprimir() {
                        if (!enUrlCorrecta()) return;

                        var botones = document.querySelectorAll('button.button-option');
                        for (var i = 0; i < botones.length; i++) {
                            var btn = botones[i];
                            var texto = (btn.textContent || '').trim().toUpperCase();
                            if (texto !== 'IMPRIMIR') continue;
                            if (btn.getAttribute(MARK) === '1') return;

                            btn.setAttribute(MARK, '1');
                            try {
                                console.log('KF_PRINT_AUTO_CLICK');
                                btn.click();
                                console.log('KF_PRINT_CLICK_OK');
                            } catch (e) {
                                console.log('KF_PRINT_ERROR:' + (e && e.message));
                            }
                            return;
                        }
                    }

                    var obs = new MutationObserver(function() { intentarImprimir(); });
                    window.__kfPrintObserver = obs;
                    obs.observe(document.documentElement, { childList: true, subtree: true });

                    var hh = function() { setTimeout(intentarImprimir, 120); };
                    window.__kfPrintHashHandler = hh;
                    window.addEventListener('hashchange', hh);

                    setTimeout(intentarImprimir, 200);
                    console.log('KF_PRINT_DETECTOR_ACTIVO');
                })();
                """;

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("expression", script);
            params.put("awaitPromise", false);
            enviar("Runtime.evaluate", params, 3000);
        } catch (Exception e) {
            System.err.println("⚠️ No se pudo inyectar el detector: " + e.getMessage());
            wsAlive = false;
        }
    }
}