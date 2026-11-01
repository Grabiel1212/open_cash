package services.tunnel;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Configuración y utilidades COMPARTIDAS del túnel CDP.
 *
 * Tanto KeyFacilMonitorService (apertura/) como ChromePrintService (impresion/)
 * consumen esta clase. Cambiar el puerto o el host aquí se refleja en ambos.
 *
 * El túnel NO se levanta desde Java: es un proxy/extensión externa que
 * expone los endpoints estándar de Chrome DevTools en 127.0.0.1:9220.
 */
public final class TunnelConfig {

    public static final int CDP_PROXY_PORT = 9220;
    public static final String CDP_BASE = "http://127.0.0.1:" + CDP_PROXY_PORT;
    public static final String KEYFACIL_HOST = "keyfacil-erp.vitekey.com";
    public static final String URL_NUEVA_VENTA = "https://" + KEYFACIL_HOST + "/#/ventas/nuevo";

    /** Cliente HTTP único (compartido entre ambos servicios). */
    public static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    /** Gson único para parsear respuestas CDP. */
    public static final Gson GSON = new GsonBuilder().create();

    private TunnelConfig() {
    }

    // =====================================================
    // DISPONIBILIDAD
    // =====================================================
    public static boolean disponible() {
        try {
            URI uri = URI.create(CDP_BASE + "/json/version");
            HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
            c.setConnectTimeout(1000);
            c.setReadTimeout(1000);
            c.connect();
            int code = c.getResponseCode();
            c.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // =====================================================
    // TARGETS
    // =====================================================
    /** Intenta /json y /json/list. Nunca devuelve null. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> obtenerTargets() {
        for (String path : new String[] { "/json", "/json/list" }) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(CDP_BASE + path)).build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200)
                    continue;
                List<Map<String, Object>> list = GSON.fromJson(resp.body(), List.class);
                if (list != null && !list.isEmpty())
                    return list;
            } catch (Exception ignored) {
            }
        }
        return Collections.emptyList();
    }

    /** Solo pestañas (type=page) cuya URL pertenece a KeyFácil y tienen WS URL. */
    public static List<Map<String, Object>> pestanasKeyFacil() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> t : obtenerTargets()) {
            Object type = t.get("type");
            Object url = t.get("url");
            Object id = t.get("id");
            Object ws = t.get("webSocketDebuggerUrl");
            if (type == null || url == null || id == null || ws == null)
                continue;
            if (!"page".equals(type.toString()))
                continue;
            if (!url.toString().contains(KEYFACIL_HOST))
                continue;
            out.add(t);
        }
        return out;
    }

    /** WS URL de una pestaña por tabId, o null si no existe. */
    public static String buscarWsUrlDePestana(String tabId) {
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

    /** WS URL del navegador (para Target.createTarget / closeTarget). */
    @SuppressWarnings("unchecked")
    public static String obtenerBrowserWsUrl() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                URI.create(CDP_BASE + "/json/version")).build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> info = GSON.fromJson(resp.body(), Map.class);
        Object ws = info.get("webSocketDebuggerUrl");
        if (ws == null)
            throw new RuntimeException("Túnel sin WebSocket del navegador");
        return ws.toString();
    }
}