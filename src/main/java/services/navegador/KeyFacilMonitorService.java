package services.navegador;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import helpers.MensajeHelper;

/**
 * Monitor de KeyFacil vía CDP.
 *
 * - Captura por EVENTOS (Runtime.consoleAPICalled) → sin polling, sin pérdidas.
 * - Una KeyFacilSession por pestaña (WebSocket persistente, un hilo por tab).
 * - El bucle principal solo crea/destruye sesiones cada 500 ms.
 * - Resistente a cierres de Chrome (reconecta automáticamente).
 */
public class KeyFacilMonitorService {

    private static final int DEBUG_PORT = 9222;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().create();

    private final VentaListenerService ventaListener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;

    private final Map<String, KeyFacilSession> sessions = new ConcurrentHashMap<>();

    public KeyFacilMonitorService(VentaListenerService ventaListener) {
        this.ventaListener = ventaListener;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void start() {
        if (!running.compareAndSet(false, true))
            return;
        worker = new Thread(this::buclePrincipal, "KeyFacilMonitor");
        worker.setDaemon(true);
        worker.start();
        MensajeHelper.info("🟢 Monitor KeyFacil iniciado (background)");
    }

    public void stop() {
        if (!running.compareAndSet(true, false))
            return;
        if (worker != null)
            worker.interrupt();

        for (KeyFacilSession s : sessions.values())
            s.close();
        sessions.clear();

        MensajeHelper.info("🛑 Monitor KeyFacil detenido");
    }

    // =====================================================
    // BUCLE PRINCIPAL (solo gestiona sesiones)
    // =====================================================
    private void buclePrincipal() {
        try {
            if (!chromeDebugDisponible()) {
                System.out.println("🟢 Iniciando Chrome para KeyFacil...");
                ProcessBuilder chrome = new ProcessBuilder(
                        obtenerRutaChrome(),
                        "--remote-debugging-port=" + DEBUG_PORT,
                        "--user-data-dir=C:\\ChromeKeyFacil",
                        "--start-maximized");
                chrome.start();

                boolean disponible = false;
                for (int i = 0; i < 20 && running.get(); i++) {
                    if (chromeDebugDisponible()) {
                        disponible = true;
                        break;
                    }
                    Thread.sleep(500);
                }
                if (!disponible) {
                    MensajeHelper.error("Chrome no inició el puerto " + DEBUG_PORT, null);
                    running.set(false);
                    return;
                }
            }

            System.out.println("🔌 Conectando con Chrome (CDP)...");

            if (!existePestanaKeyFacil()) {
                crearPestana("https://keyfacil-erp.vitekey.com/#/ventas/nuevo");
                Thread.sleep(1500);
            }

            System.out.println();
            System.out.println("======================================");
            System.out.println("🟢 CHROME CONECTADO (desde Home)");
            System.out.println("======================================");
            System.out.println("👉 Captura por EVENTOS (instantánea)");
            System.out.println("👉 Abre/cierra pestañas sin perder ventas.");
            System.out.println("👉 Los comandos Ctrl+PIN+Win siguen activos.");
            System.out.println();
            System.out.println("🟢 ESPERANDO PROCESANDO VENTA...");
            System.out.println();

            boolean chromeEstabaCaido = false;

            while (running.get()) {
                List<Map<String, Object>> targets;
                try {
                    targets = obtenerTargets();
                    if (chromeEstabaCaido) {
                        System.out.println();
                        System.out.println("🔄 Chrome reconectado. Reanudando captura...");
                        chromeEstabaCaido = false;
                    }
                } catch (Exception e) {
                    if (!chromeEstabaCaido) {
                        System.out.println();
                        System.out.println("⚠️ Chrome no responde. Esperando reconexión...");
                        chromeEstabaCaido = true;
                    }
                    for (KeyFacilSession s : sessions.values())
                        s.close();
                    sessions.clear();
                    Thread.sleep(1000);
                    continue;
                }

                Set<String> idsActuales = new HashSet<>();
                for (Map<String, Object> t : targets) {
                    Object id = t.get("id");
                    if (id != null)
                        idsActuales.add(id.toString());
                }

                // Cerrar sesiones huérfanas / muertas
                Iterator<Map.Entry<String, KeyFacilSession>> it = sessions.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, KeyFacilSession> e = it.next();
                    KeyFacilSession s = e.getValue();
                    if (!idsActuales.contains(e.getKey()) || !s.isAlive()) {
                        s.close();
                        it.remove();
                    }
                }

                // Crear sesiones nuevas
                for (Map<String, Object> t : targets) {
                    try {
                        Object type = t.get("type");
                        Object urlObj = t.get("url");
                        Object idObj = t.get("id");
                        Object wsObj = t.get("webSocketDebuggerUrl");

                        if (type == null || urlObj == null || idObj == null || wsObj == null)
                            continue;
                        if (!"page".equals(type.toString()))
                            continue;

                        String url = urlObj.toString();
                        if (!url.contains("keyfacil-erp.vitekey.com"))
                            continue;

                        String id = idObj.toString();
                        String wsUrl = wsObj.toString();

                        if (sessions.containsKey(id))
                            continue;

                        try {
                            KeyFacilSession session = new KeyFacilSession(id, wsUrl, ventaListener);
                            sessions.put(id, session);
                            session.start();

                            System.out.println();
                            System.out.println("🟢 Monitor activo en:");
                            System.out.println("   " + id);
                            System.out.println("   " + url);

                        } catch (Exception e) {
                            // Falló: se reintentará en el próximo ciclo
                        }

                    } catch (Exception ignored) {
                    }
                }

                Thread.sleep(500);
            }

        } catch (InterruptedException ie) {
            // cierre limpio
        } catch (Exception e) {
            MensajeHelper.error("Error en monitor KeyFacil: " + e.getMessage(), e);
        } finally {
            running.set(false);
            for (KeyFacilSession s : sessions.values())
                s.close();
            sessions.clear();
        }
    }

    // =====================================================
    // CDP HELPERS
    // =====================================================
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> obtenerTargets() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + DEBUG_PORT + "/json")).build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return GSON.fromJson(resp.body(), List.class);
    }

    private boolean existePestanaKeyFacil() throws Exception {
        for (Map<String, Object> t : obtenerTargets()) {
            Object type = t.get("type");
            Object url = t.get("url");
            if (type != null && url != null
                    && "page".equals(type.toString())
                    && url.toString().contains("keyfacil-erp.vitekey.com")) {
                return true;
            }
        }
        return false;
    }

    private void crearPestana(String url) throws Exception {
        final WebSocket[] wsRef = new WebSocket[1];
        final CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();

        wsRef[0] = HTTP.newWebSocketBuilder()
                .buildAsync(URI.create(obtenerBrowserWsUrl()), new WebSocket.Listener() {
                    final StringBuilder buffer = new StringBuilder();

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
                                Map<String, Object> parsed = GSON.fromJson(msg, Map.class);
                                Object idObj = parsed.get("id");
                                if (idObj instanceof Number && ((Number) idObj).intValue() == 1) {
                                    future.complete(parsed);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        webSocket.request(1);
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

    private boolean chromeDebugDisponible() {
        try {
            URI uri = URI.create("http://127.0.0.1:" + DEBUG_PORT + "/json/version");
            HttpURLConnection conexion = (HttpURLConnection) uri.toURL().openConnection();
            conexion.setConnectTimeout(500);
            conexion.setReadTimeout(500);
            conexion.connect();
            int codigo = conexion.getResponseCode();
            conexion.disconnect();
            return codigo == 200;
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
        for (String ruta : rutas) {
            if (new File(ruta).exists())
                return ruta;
        }
        throw new RuntimeException("No se encontró Google Chrome.");
    }

    // =====================================================
    // API PÚBLICA: abrir/reabrir Chrome
    // =====================================================
    public void abrirNavegadorKeyFacil() {
        new Thread(() -> {
            try {
                boolean yaCorriendo = chromeDebugDisponible();

                if (!yaCorriendo) {
                    System.out.println("🟢 Abriendo Chrome KeyFacil (manual)...");
                    ProcessBuilder chrome = new ProcessBuilder(
                            obtenerRutaChrome(),
                            "--remote-debugging-port=" + DEBUG_PORT,
                            "--user-data-dir=C:\\ChromeKeyFacil",
                            "--start-maximized");
                    chrome.start();

                    boolean listo = false;
                    for (int i = 0; i < 20; i++) {
                        if (chromeDebugDisponible()) {
                            listo = true;
                            break;
                        }
                        Thread.sleep(500);
                    }
                    if (!listo) {
                        MensajeHelper.error("Chrome no levantó el puerto " + DEBUG_PORT, null);
                        return;
                    }
                }

                if (!existePestanaKeyFacil()) {
                    crearPestana("https://keyfacil-erp.vitekey.com/#/ventas/nuevo");
                    Thread.sleep(1500);
                }

                MensajeHelper.info("🌐 Navegador KeyFacil abierto");

            } catch (Exception e) {
                MensajeHelper.error("No se pudo abrir el navegador: " + e.getMessage(), e);
            }
        }, "AbrirNavegadorKeyFacil").start();
    }
}