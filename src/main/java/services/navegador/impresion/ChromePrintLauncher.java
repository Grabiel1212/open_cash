package services.navegador.impresion;

import java.io.File;
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
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Arranque y gestión del proceso Chrome (con perfil y puerto CDP propios).
 */
final class ChromePrintLauncher {

    static final int DEBUG_PORT = 9223;
    static final String USER_DATA_DIR = "C:\\ChromePrintKeyFacil";
    static final String KEYFACIL_HOST = "keyfacil-erp.vitekey.com";
    static final String URL_NUEVA_VENTA = "https://" + KEYFACIL_HOST + "/#/ventas/nuevo";

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().create();

    private ChromePrintLauncher() {
    }

    // =====================================================
    // Asegurar Chrome
    // =====================================================
    static boolean asegurarChrome() throws Exception {
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

    private static void lanzarChrome() throws Exception {
        ProcessBuilder chrome = new ProcessBuilder(
                obtenerRutaChrome(),
                "--remote-debugging-port=" + DEBUG_PORT,
                "--user-data-dir=" + USER_DATA_DIR,
                "--kiosk-printing",
                "--start-maximized",
                URL_NUEVA_VENTA);
        chrome.start();
    }

    private static boolean esperarDebugDisponible(int segundos) throws InterruptedException {
        for (int i = 0; i < segundos * 2; i++) {
            if (chromeDebugDisponible())
                return true;
            Thread.sleep(500);
        }
        return chromeDebugDisponible();
    }

    private static void matarChromeConNuestroPerfil() {
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

    static boolean chromeDebugDisponible() {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) URI
                    .create("http://127.0.0.1:" + DEBUG_PORT + "/json/version")
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

    private static String obtenerRutaChrome() {
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
    // Targets / pestañas
    // =====================================================
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> obtenerTargets() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + DEBUG_PORT + "/json")).build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return GSON.fromJson(resp.body(), List.class);
    }

    /** Devuelve el id de la primera pestaña de KeyFácil, o null si no hay. */
    static String buscarPrimeraPestanaKeyFacil() throws Exception {
        for (Map<String, Object> t : obtenerTargets()) {
            Object type = t.get("type");
            Object url = t.get("url");
            Object id = t.get("id");
            if (type == null || url == null || id == null)
                continue;
            if (!"page".equals(type.toString()))
                continue;
            if (!url.toString().contains(KEYFACIL_HOST))
                continue;
            return id.toString();
        }
        return null;
    }

    static boolean existePestana(String tabId) throws Exception {
        if (tabId == null)
            return false;
        for (Map<String, Object> t : obtenerTargets()) {
            Object id = t.get("id");
            if (id != null && id.toString().equals(tabId))
                return true;
        }
        return false;
    }

    static String obtenerWsUrlDePestana(String tabId) throws Exception {
        if (tabId == null)
            return null;
        for (Map<String, Object> t : obtenerTargets()) {
            Object id = t.get("id");
            if (id == null || !id.toString().equals(tabId))
                continue;
            Object ws = t.get("webSocketDebuggerUrl");
            return ws != null ? ws.toString() : null;
        }
        return null;
    }

    static void crearPestana(String url) throws Exception {
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
    private static String obtenerBrowserWsUrl() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + DEBUG_PORT + "/json/version")).build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> info = GSON.fromJson(resp.body(), Map.class);
        Object ws = info.get("webSocketDebuggerUrl");
        if (ws == null)
            throw new RuntimeException("Sin WebSocket del navegador");
        return ws.toString();
    }
}