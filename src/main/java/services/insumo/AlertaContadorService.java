package services.insumo;

import services.text_vos.TextPeechService;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Alerta sonora + voz que se repite en bucle mientras no exista
 * ningún contador inicial (Ricoh o Epson).
 * NO bloquea navegadores, solo insiste con audio.
 */
public class AlertaContadorService {

    // ============================================================
    // MENSAJES Y AUDIOS
    // ============================================================
    private static final String MENSAJE_PEDIR = "Por favor, ingrese el contador inicial de hojas";
    private static final String MENSAJE_GRACIAS = "Cantidad inicial registrada";
    private static final String MENSAJE_SUSPENDIDO = "Registro suspendido";

    private static final File AUDIO_PEDIR = new File("audios_empleados/alerta_pedir.wav");
    private static final File AUDIO_GRACIAS = new File("audios_empleados/alerta_gracias.wav");
    private static final File AUDIO_SUSPENDIDO = new File("audios_empleados/alerta_suspendido.wav");

    private final ControlInsumoService controlInsumoService = ControlInsumoService.getInstance();

    private final AtomicBoolean activo = new AtomicBoolean(false);
    private volatile Thread hiloAlerta;
    private volatile Clip clipActual;

    // ============================================================
    // API PÚBLICA
    // ============================================================

    public synchronized void iniciar() {
        if (controlInsumoService.hayAlgunContador())
            return;
        if (!activo.compareAndSet(false, true))
            return;

        hiloAlerta = new Thread(this::bucleAlerta, "alerta-contador");
        hiloAlerta.setDaemon(true);
        hiloAlerta.start();
    }

    /**
     * Detiene la alarma y reproduce «Cantidad inicial registrada» (aunque ya
     * estuviera silenciada).
     */
    public synchronized void detenerConAgradecimiento() {
        if (!activo.getAndSet(false)) {
            reproducirDespedidaAsync(MENSAJE_GRACIAS, AUDIO_GRACIAS, "alerta-gracias");
            return;
        }
        detenerInterno(MENSAJE_GRACIAS, AUDIO_GRACIAS, "alerta-gracias");
    }

    /**
     * Detiene la alarma y reproduce «Registro suspendido» (aunque ya estuviera
     * silenciada).
     */
    public synchronized void detenerConSuspension() {
        if (!activo.getAndSet(false)) {
            reproducirDespedidaAsync(MENSAJE_SUSPENDIDO, AUDIO_SUSPENDIDO, "alerta-suspendido");
            return;
        }
        detenerInterno(MENSAJE_SUSPENDIDO, AUDIO_SUSPENDIDO, "alerta-suspendido");
    }

    /** Silencia SIN reproducir audio. El bloqueo del controller sigue activo. */
    public synchronized void silencioManual() {
        if (!activo.getAndSet(false))
            return;
        detenerClipActual();
        final Thread t = hiloAlerta;
        hiloAlerta = null;
        new Thread(() -> {
            if (t != null) {
                try {
                    t.join(2000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "alerta-silencio").start();
    }

    /**
     * Parada defensiva: detiene la alarma SIN reproducir audio.
     * Antes reproducía «gracias», lo cual era incorrecto cuando se usaba
     * como cierre de seguridad sin haber guardado.
     */
    public synchronized void detener() {
        silencioManual();
    }

    // ============================================================
    // ALARMA ESTÁTICA (MainApp u otros)
    // ============================================================

    /** Reproduce alert.wav / alerta.wav UNA vez. Bloquea hasta 15s. */
    public static void reproducirAlarmaUnaVez() {
        File archivo = localizarAlarmaArchivo();
        if (archivo == null) {
            System.out.println("⚠️ [AlertaContador] No se encontró alert.wav / alerta.wav");
            return;
        }
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(archivo)) {
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            CountDownLatch fin = new CountDownLatch(1);
            clip.addLineListener(e -> {
                if (e.getType() == LineEvent.Type.STOP)
                    fin.countDown();
            });
            clip.start();
            fin.await(15, TimeUnit.SECONDS);
            try {
                clip.stop();
            } catch (Exception ignored) {
            }
            try {
                clip.close();
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            System.out.println("⚠️ [alarma-static] Java Sound falló: " + e.getMessage());
            try {
                new ProcessBuilder("powershell.exe", "-NoProfile", "-Command",
                        "(New-Object Media.SoundPlayer '" + archivo.getAbsolutePath() + "').PlaySync()")
                        .start().waitFor();
            } catch (Exception ignored) {
            }
        }
    }

    // ============================================================
    // BUCLE DE ALERTA
    // ============================================================

    private void bucleAlerta() {
        System.out.println("🔔 [AlertaContador] Bucle iniciado");
        logDispositivosAudio();

        File archivoAlarma = localizarAlarmaArchivo();
        if (archivoAlarma == null) {
            System.out.println("❌ [AlertaContador] No se encontró alert.wav / alerta.wav");
            System.out.println("   Buscado en classpath:/audios/ y en resources/audios/, "
                    + "src/main/resources/audios/, audios/");
        } else {
            System.out.println("✅ [AlertaContador] Usando alarma: "
                    + archivoAlarma.getAbsolutePath());
        }

        asegurarAudio(MENSAJE_PEDIR, AUDIO_PEDIR);

        while (activo.get()) {
            if (controlInsumoService.hayAlgunContador())
                break;

            if (archivoAlarma != null)
                reproducirAlarma(archivoAlarma);
            if (!activo.get())
                break;

            if (AUDIO_PEDIR.exists())
                reproducirYEsperarArchivo(AUDIO_PEDIR, "voz-pedir");
            if (!activo.get())
                break;

            dormir(300);
        }
        System.out.println("🔕 [AlertaContador] Bucle terminado");
    }

    // ============================================================
    // INTERNO - Pipeline de parada
    // ============================================================

    private void detenerInterno(String mensaje, File audio, String nombreHilo) {
        detenerClipActual();
        final Thread t = hiloAlerta;
        hiloAlerta = null;
        new Thread(() -> {
            if (t != null) {
                try {
                    t.join(2000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            reproducirDespedidaSync(mensaje, audio);
        }, nombreHilo).start();
    }

    private void reproducirDespedidaAsync(String mensaje, File audio, String nombreHilo) {
        new Thread(() -> reproducirDespedidaSync(mensaje, audio), nombreHilo).start();
    }

    private void reproducirDespedidaSync(String mensaje, File audio) {
        try {
            asegurarAudio(mensaje, audio);
            if (audio.exists())
                reproducirSimple(audio);
        } catch (Exception e) {
            System.out.println("⚠️ Error al reproducir despedida: " + e.getMessage());
        }
    }

    // ============================================================
    // INTERNO - Reproducción
    // ============================================================

    private void reproducirAlarma(File archivo) {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(archivo)) {
            AudioFormat f = ais.getFormat();
            System.out.println("🔊 [alarma] " + archivo.getName()
                    + "  ->  " + (int) f.getSampleRate() + "Hz, "
                    + f.getSampleSizeInBits() + " bits, "
                    + f.getChannels() + " canal(es), "
                    + f.getEncoding());
            reproducirYEsperarStream(ais);
            return;
        } catch (Exception e) {
            System.out.println("⚠️ [alarma] Java Sound falló: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("   Intentando con PowerShell...");
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-Command",
                    "(New-Object Media.SoundPlayer '" + archivo.getAbsolutePath() + "').PlaySync()");
            pb.redirectErrorStream(true);
            pb.start().waitFor();
        } catch (Exception e) {
            System.out.println("❌ [alarma] PowerShell también falló: " + e.getMessage());
            dormir(500);
        }
    }

    private void reproducirYEsperarArchivo(File archivo, String etiqueta) {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(archivo)) {
            reproducirYEsperarStream(ais);
        } catch (Exception e) {
            System.out.println("⚠️ [" + etiqueta + "] error: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "powershell.exe", "-NoProfile", "-Command",
                        "(New-Object Media.SoundPlayer '" + archivo.getAbsolutePath() + "').PlaySync()");
                pb.redirectErrorStream(true);
                pb.start().waitFor();
            } catch (Exception ignored) {
            }
        }
    }

    private void reproducirYEsperarStream(AudioInputStream ais) throws Exception {
        Clip clip = AudioSystem.getClip();
        clip.open(ais);
        clipActual = clip;

        CountDownLatch fin = new CountDownLatch(1);
        clip.addLineListener(e -> {
            if (e.getType() == LineEvent.Type.STOP)
                fin.countDown();
        });

        clip.start();
        while (activo.get() && fin.getCount() > 0) {
            fin.await(100, TimeUnit.MILLISECONDS);
        }

        try {
            clip.stop();
        } catch (Exception ignored) {
        }
        try {
            clip.close();
        } catch (Exception ignored) {
        }
        if (clipActual == clip)
            clipActual = null;
    }

    private void reproducirSimple(File archivo) {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(archivo)) {
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            clip.addLineListener(e -> {
                if (e.getType() == LineEvent.Type.STOP) {
                    try {
                        clip.close();
                    } catch (Exception ignored) {
                    }
                }
            });
            clip.start();
        } catch (Exception e) {
            System.out.println("⚠️ [despedida] error: " + e.getMessage());
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "powershell.exe", "-NoProfile", "-Command",
                        "(New-Object Media.SoundPlayer '" + archivo.getAbsolutePath() + "').PlaySync()");
                pb.start().waitFor();
            } catch (Exception ignored) {
            }
        }
    }

    private void detenerClipActual() {
        Clip c = clipActual;
        clipActual = null;
        if (c != null) {
            try {
                c.stop();
            } catch (Exception ignored) {
            }
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ============================================================
    // INTERNO - Archivos y generación de voz
    // ============================================================

    private static File localizarAlarmaArchivo() {
        String[] nombres = { "alert.wav", "alerta.wav" };
        String[] bases = {
                "resources/audios/",
                "src/main/resources/audios/",
                "audios/",
                "src/audios/",
                "./audios/"
        };

        for (String base : bases) {
            for (String nombre : nombres) {
                File f = new File(base + nombre);
                if (f.exists() && f.length() > 0)
                    return f;
            }
        }

        for (String nombre : nombres) {
            try (InputStream is = AlertaContadorService.class
                    .getResourceAsStream("/audios/" + nombre)) {
                if (is == null)
                    continue;
                File tmp = File.createTempFile("alerta_", ".wav");
                tmp.deleteOnExit();
                try (OutputStream os = new FileOutputStream(tmp)) {
                    is.transferTo(os);
                }
                return tmp;
            } catch (Exception e) {
                System.out.println("⚠️ Error copiando " + nombre
                        + " desde classpath: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Asegura que el WAV exista Y coincida con el texto actual.
     * Usa un sidecar .txt para detectar cambios en el mensaje y regenerar
     * automáticamente. Así, si cambias una constante de texto, el audio
     * se actualiza la próxima vez sin que tengas que borrar el archivo a mano.
     */
    private void asegurarAudio(String mensaje, File audio) {
        File sidecar = new File(audio.getParentFile(), audio.getName() + ".txt");
        try {
            if (audio.exists() && sidecar.exists()) {
                String anterior = Files.readString(sidecar.toPath()).trim();
                if (anterior.equals(mensaje))
                    return; // coincide → no tocar
                System.out.println("🔄 [AlertaContador] Texto cambió → regenerando "
                        + audio.getName());
            }
        } catch (Exception ignored) {
        }

        generarVoz(mensaje, audio);

        try {
            File padre = sidecar.getParentFile();
            if (padre != null && !padre.exists())
                padre.mkdirs();
            Files.writeString(sidecar.toPath(), mensaje);
        } catch (Exception e) {
            System.out.println("⚠️ No se pudo escribir sidecar " + sidecar
                    + ": " + e.getMessage());
        }
    }

    private void generarVoz(String texto, File destino) {
        try {
            File padre = destino.getParentFile();
            if (padre != null && !padre.exists())
                padre.mkdirs();
            TextPeechService.generarAudio(texto, destino);
        } catch (Exception e) {
            System.out.println("❌ No se pudo generar la voz: " + e.getMessage());
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void logDispositivosAudio() {
        try {
            Mixer.Info[] mixers = AudioSystem.getMixerInfo();
            System.out.println("🔈 [AlertaContador] Dispositivos de audio: " + mixers.length);
            for (Mixer.Info m : mixers)
                System.out.println("    - " + m.getName());
            if (mixers.length == 0) {
                System.out.println("⚠️ ¡No hay dispositivos de audio! "
                        + "Revisa que el altavoz esté conectado.");
            }
        } catch (Exception e) {
            System.out.println("⚠️ No se pudieron listar dispositivos: " + e.getMessage());
        }
    }
}