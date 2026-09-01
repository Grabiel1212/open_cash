package services.text_vos;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class TextPeechService {

        // =====================================================
        // PIPER
        // =====================================================

        private static final String PIPER = "piper\\piper.exe";

        // =====================================================
        // MODELO DE VOZ
        // =====================================================

        private static final String MODELO = "piper\\es_AR-daniela-high.onnx";

        // =====================================================
        // CARPETA AUDIOS DE EMPLEADOS
        // =====================================================

        private static final String CARPETA_AUDIOS = "audios_empleados";

        // =====================================================
        // MÉTODO 1
        // GENERAR AUDIO TEMPORAL
        //
        // Genera -> Reproduce -> Elimina
        // =====================================================

        public static void hablar(String texto) {

                if (texto == null || texto.trim().isEmpty()) {
                        return;
                }

                File audio = null;

                try {

                        // =================================================
                        // CREAR AUDIO TEMPORAL
                        // =================================================

                        audio = File.createTempFile(
                                        "voz_",
                                        ".wav");

                        System.out.println("🔊 Generando voz temporal...");

                        // =================================================
                        // GENERAR CON PIPER
                        // =================================================

                        boolean generado = generarAudio(
                                        texto,
                                        audio);

                        if (!generado) {
                                return;
                        }

                        // =================================================
                        // REPRODUCIR
                        // =================================================

                        reproducir(audio);

                        System.out.println(
                                        "✅ Reproducción terminada.");

                } catch (Exception e) {

                        System.out.println(
                                        "❌ Error en TextPeechService:");

                        e.printStackTrace();

                } finally {

                        // =================================================
                        // ELIMINAR AUDIO TEMPORAL
                        // =================================================

                        if (audio != null && audio.exists()) {

                                if (audio.delete()) {

                                        System.out.println(
                                                        "🗑️ Audio temporal eliminado.");

                                } else {

                                        System.out.println(
                                                        "⚠️ No se pudo eliminar el audio temporal:");

                                        System.out.println(
                                                        audio.getAbsolutePath());
                                }
                        }
                }
        }

        // =====================================================
        // MÉTODO 2
        // GENERAR AUDIO PERMANENTE DEL EMPLEADO
        //
        // Ejemplo:
        //
        // audios_empleados/
        // empleado_1.wav
        // empleado_2.wav
        // empleado_15.wav
        //
        // Si ya existe NO lo vuelve a generar
        // =====================================================

        public static File generarAudioEmpleado(
                        Long idEmpleado,
                        String texto) {

                if (texto == null || texto.trim().isEmpty()) {
                        return null;
                }

                try {

                        // =================================================
                        // CREAR CARPETA SI NO EXISTE
                        // =================================================

                        File carpeta = new File(
                                        CARPETA_AUDIOS);

                        if (!carpeta.exists()) {

                                boolean creada = carpeta.mkdirs();

                                if (!creada) {

                                        System.out.println(
                                                        "❌ No se pudo crear la carpeta de audios.");

                                        return null;
                                }
                        }

                        // =================================================
                        // ARCHIVO DEL EMPLEADO
                        // =================================================

                        File audioEmpleado = new File(
                                        carpeta,
                                        "empleado_" + idEmpleado + ".wav");

                        // =================================================
                        // SI YA EXISTE NO GENERAR OTRA VEZ
                        // =================================================

                        if (audioEmpleado.exists()) {

                                System.out.println(
                                                "🔊 Audio del empleado ya existe:");

                                System.out.println(
                                                audioEmpleado.getAbsolutePath());

                                return audioEmpleado;
                        }

                        // =================================================
                        // GENERAR AUDIO POR PRIMERA VEZ
                        // =================================================

                        System.out.println(
                                        "🎤 Generando audio para empleado ID: "
                                                        + idEmpleado);

                        boolean generado = generarAudio(
                                        texto,
                                        audioEmpleado);

                        if (!generado) {
                                return null;
                        }

                        System.out.println(
                                        "✅ Audio del empleado guardado:");

                        System.out.println(
                                        audioEmpleado.getAbsolutePath());

                        return audioEmpleado;

                } catch (Exception e) {

                        System.out.println(
                                        "❌ Error al generar audio del empleado:");

                        e.printStackTrace();

                        return null;
                }
        }

        // =====================================================
        // MÉTODO 3
        // REPRODUCIR AUDIO DEL EMPLEADO
        //
        // Si existe lo reproduce
        // Si no existe devuelve false
        // =====================================================

        public static boolean reproducirAudioEmpleado(
                        Long idEmpleado) {

                try {

                        File audioEmpleado = new File(
                                        CARPETA_AUDIOS,
                                        "empleado_" + idEmpleado + ".wav");

                        // =================================================
                        // VERIFICAR AUDIO
                        // =================================================

                        if (!audioEmpleado.exists()) {

                                System.out.println(
                                                "⚠️ No existe audio para empleado ID: "
                                                                + idEmpleado);

                                return false;
                        }

                        // =================================================
                        // REPRODUCIR
                        // =================================================

                        System.out.println(
                                        "🔊 Reproduciendo audio del empleado...");

                        reproducir(audioEmpleado);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "❌ Error al reproducir audio del empleado:");

                        e.printStackTrace();

                        return false;
                }
        }

        // =====================================================
        // MÉTODO PRINCIPAL
        // GENERAR AUDIO CON PIPER
        //
        // Este método interno sirve para los dos casos:
        //
        // - Audio temporal
        // - Audio permanente
        // =====================================================

        private static boolean generarAudio(
                        String texto,
                        File archivoSalida) {

                if (texto == null || texto.trim().isEmpty()) {
                        return false;
                }

                try {

                        File archivoPiper = new File(PIPER);

                        File archivoModelo = new File(MODELO);

                        // =================================================
                        // VERIFICAR PIPER
                        // =================================================

                        if (!archivoPiper.exists()) {

                                System.out.println(
                                                "❌ No se encontró Piper:");

                                System.out.println(
                                                archivoPiper.getAbsolutePath());

                                return false;
                        }

                        // =================================================
                        // VERIFICAR MODELO
                        // =================================================

                        if (!archivoModelo.exists()) {

                                System.out.println(
                                                "❌ No se encontró el modelo de voz:");

                                System.out.println(
                                                archivoModelo.getAbsolutePath());

                                return false;
                        }

                        // =================================================
                        // EJECUTAR PIPER
                        // =================================================

                        ProcessBuilder pb = new ProcessBuilder(

                                        archivoPiper.getAbsolutePath(),

                                        "--model",

                                        archivoModelo.getAbsolutePath(),

                                        "--output_file",

                                        archivoSalida.getAbsolutePath());

                        pb.redirectErrorStream(true);

                        Process proceso = pb.start();

                        // =================================================
                        // ENVIAR TEXTO A PIPER
                        // =================================================

                        try (OutputStream entrada = proceso.getOutputStream()) {

                                entrada.write(

                                                (texto + "\n")
                                                                .getBytes(
                                                                                StandardCharsets.UTF_8));
                        }

                        // =================================================
                        // ESPERAR FINALIZACIÓN
                        // =================================================

                        int resultado = proceso.waitFor();

                        if (resultado != 0) {

                                System.out.println(
                                                "❌ Piper no pudo generar el audio.");

                                return false;
                        }

                        // =================================================
                        // VERIFICAR ARCHIVO
                        // =================================================

                        if (!archivoSalida.exists()
                                        || archivoSalida.length() == 0) {

                                System.out.println(
                                                "❌ El archivo WAV no fue generado correctamente.");

                                return false;
                        }

                        System.out.println(
                                        "✅ Audio generado correctamente.");

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "❌ Error ejecutando Piper:");

                        e.printStackTrace();

                        return false;
                }
        }

        // =====================================================
        // REPRODUCIR AUDIO
        // =====================================================

        private static void reproducir(
                        File archivo) throws Exception {

                if (archivo == null || !archivo.exists()) {

                        System.out.println(
                                        "❌ Archivo de audio no encontrado.");

                        return;
                }

                ProcessBuilder reproductor = new ProcessBuilder(

                                "powershell.exe",

                                "-NoProfile",

                                "-Command",

                                "(New-Object Media.SoundPlayer '" +
                                                archivo.getAbsolutePath() +
                                                "').PlaySync()");

                reproductor.inheritIO();

                Process procesoAudio = reproductor.start();

                procesoAudio.waitFor();
        }
}