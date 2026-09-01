package services.cash;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import helpers.MensajeHelper;
import javafx.application.Platform;

public class HotkeyManager implements NativeKeyListener {

    private final ConfigManagerDB configManager;
    private final Runnable openDrawerCallback;

    private boolean listening = false;

    private boolean ctrlDown = false;
    private boolean altDown = false;
    private boolean windowsKeyDown = false;

    private boolean sessionActive = false;

    private boolean updateMode = false;

    private int altPressCount = 0;
    private long lastAltPressTime = 0;

    private final StringBuilder pinBuffer = new StringBuilder();
    private final StringBuilder updatePinBuffer = new StringBuilder();

    private Timer sessionTimer;
    private Timer altTimer;

    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public HotkeyManager(
            ConfigManagerDB configManager,
            Runnable openDrawerCallback) {

        this.configManager = configManager;
        this.openDrawerCallback = openDrawerCallback;

        // Silenciar logs molestos de JNativeHook
        Logger logger = Logger.getLogger(
                GlobalScreen.class.getPackage().getName());

        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
    }

    // =====================================================
    // ESTADO
    // =====================================================

    public boolean isListening() {

        return listening;
    }

    // =====================================================
    // INICIAR ESCUCHA GLOBAL
    // =====================================================

    public void startListening() {

        if (listening) {
            return;
        }

        try {

            if (!GlobalScreen.isNativeHookRegistered()) {

                GlobalScreen.registerNativeHook();
            }

            GlobalScreen.addNativeKeyListener(this);

            listening = true;

            MensajeHelper.info(
                    "Escucha global activada (Ctrl + PIN + Win)");

        } catch (NativeHookException ex) {

            MensajeHelper.error(
                    "No se pudo registrar hook global: "
                            + ex.getMessage(),
                    ex);
        }
    }

    // =====================================================
    // DETENER ESCUCHA GLOBAL
    // =====================================================

    public void stopListening() {

        if (!listening) {
            return;
        }

        try {

            GlobalScreen.removeNativeKeyListener(this);

            listening = false;

            if (sessionTimer != null) {

                sessionTimer.cancel();
                sessionTimer = null;
            }

            if (altTimer != null) {

                altTimer.cancel();
                altTimer = null;
            }

            sessionActive = false;

            MensajeHelper.info(
                    "Escucha global detenida");

        } catch (Exception ex) {

            MensajeHelper.error(
                    "No se pudo detener la escucha: "
                            + ex.getMessage(),
                    ex);
        }
    }

    // =====================================================
    // TECLA PRESIONADA
    // =====================================================

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {

        int code = e.getKeyCode();

        String keyText = NativeKeyEvent.getKeyText(code);

        ctrlDown = (e.getModifiers()
                & NativeKeyEvent.CTRL_MASK) != 0;

        altDown = (e.getModifiers()
                & NativeKeyEvent.ALT_MASK) != 0;

        // =================================================
        // CTRL + X = CERRAR SESIÓN
        // =================================================

        if (ctrlDown
                && code == NativeKeyEvent.VC_X) {

            cancelSession();

            return;
        }

        // =================================================
        // CTRL + ALT + ALT = MODO CAMBIO DE PIN
        // =================================================

        if (ctrlDown
                && code == NativeKeyEvent.VC_ALT) {

            long currentTime = System.currentTimeMillis();

            if (currentTime - lastAltPressTime < 500) {

                altPressCount++;

                if (altPressCount == 2) {

                    updateMode = true;

                    updatePinBuffer.setLength(0);

                    MensajeHelper.info(
                            "Modo actualización de PIN activado");

                    showNotification(
                            "Modo actualización PIN activado. "
                                    + "Ingrese nuevo PIN y presione Windows.");

                    altPressCount = 0;
                }

            } else {

                altPressCount = 1;
            }

            lastAltPressTime = currentTime;

            return;
        }

        // =================================================
        // EVITAR EVENTOS DUPLICADOS DE WINDOWS
        // =================================================

        if (isWindowsKey(keyText)) {

            if (windowsKeyDown) {

                return;
            }

            windowsKeyDown = true;
        }

        // =================================================
        // MODO ACTUALIZACIÓN DE PIN
        // =================================================

        if (updateMode) {

            // Capturar caracteres del nuevo PIN
            if (keyText != null
                    && keyText.length() == 1
                    && !Character.isISOControl(
                            keyText.charAt(0))) {

                updatePinBuffer.append(
                        keyText.charAt(0));

                System.out.print("*");

                return;
            }

            // ---------------------------------------------
            // WINDOWS = FINALIZAR NUEVO PIN
            // ---------------------------------------------

            if (isWindowsKey(keyText)) {

                String newPin = updatePinBuffer.toString();

                if (newPin.length() >= 2) {

                    if (configManager.actualizarPin(newPin)) {

                        MensajeHelper.info(
                                "PIN actualizado exitosamente");

                        // Abrir caja inmediatamente
                        if (openDrawerCallback != null) {

                            Platform.runLater(
                                    openDrawerCallback);
                        }

                        // Cerrar sesión después de cambiar PIN
                        cancelSession();

                        MensajeHelper.info(
                                "Sesión cerrada automáticamente "
                                        + "tras actualización de PIN");

                    } else {

                        MensajeHelper.advertencia(
                                "Error al actualizar PIN");
                    }

                } else {

                    MensajeHelper.advertencia(
                            "PIN demasiado corto. "
                                    + "Mínimo 2 caracteres");
                }

                updateMode = false;

                updatePinBuffer.setLength(0);
            }

        }

        // =================================================
        // MODO NORMAL
        // =================================================

        else if (ctrlDown) {

            // ---------------------------------------------
            // CAPTURAR PIN
            // ---------------------------------------------

            if (keyText != null
                    && keyText.length() == 1
                    && !Character.isISOControl(
                            keyText.charAt(0))) {

                pinBuffer.append(
                        keyText.charAt(0));

                System.out.print("*");

                return;
            }

            // ---------------------------------------------
            // WINDOWS = FINALIZAR PIN
            // ---------------------------------------------

            if (isWindowsKey(keyText)) {

                // =========================================
                // SESIÓN YA ACTIVA
                // =========================================

                if (sessionActive) {

                    if (openDrawerCallback != null) {

                        Platform.runLater(
                                openDrawerCallback);
                    }

                    MensajeHelper.info(
                            "Caja abierta mediante "
                                    + "Ctrl + Windows "
                                    + "(sesión activa)");

                }

                // =========================================
                // NO HAY SESIÓN
                // =========================================

                else {

                    String enteredPin = pinBuffer.toString();

                    if (configManager.validarPin(
                            enteredPin)) {

                        MensajeHelper.info(
                                "PIN correcto");

                        if (openDrawerCallback != null) {

                            Platform.runLater(
                                    openDrawerCallback);
                        }

                        startSession();

                        showNotification(
                                "Sesión activada. "
                                        + "Ahora basta Ctrl + Win "
                                        + "para abrir.");

                    } else {

                        MensajeHelper.advertencia(
                                "PIN incorrecto");
                    }
                }

                // Limpiar PIN siempre
                pinBuffer.setLength(0);
            }
        }
    }

    // =====================================================
    // TECLA LIBERADA
    // =====================================================

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {

        int code = e.getKeyCode();

        String keyText = NativeKeyEvent.getKeyText(code);

        // Windows
        if (isWindowsKey(keyText)) {

            windowsKeyDown = false;
        }

        // Alt
        if (code == NativeKeyEvent.VC_ALT) {

            altDown = false;
        }

        // Ctrl
        if (code == NativeKeyEvent.VC_CONTROL) {

            ctrlDown = false;

            // Limpiar PIN
            if (pinBuffer.length() > 0) {

                pinBuffer.setLength(0);

                MensajeHelper.info(
                        "Buffer limpiado (Ctrl liberado)");
            }

            // Cancelar actualización
            if (updatePinBuffer.length() > 0) {

                updateMode = false;

                updatePinBuffer.setLength(0);

                MensajeHelper.info(
                        "Modo actualización cancelado "
                                + "(Ctrl liberado)");
            }
        }
    }

    // =====================================================
    // TECLA ESCRITA
    // =====================================================

    @Override
    public void nativeKeyTyped(
            NativeKeyEvent nativeKeyEvent) {

        // No usado
    }

    // =====================================================
    // DETECTAR WINDOWS / META
    // =====================================================

    private boolean isWindowsKey(String keyText) {

        if (keyText == null) {

            return false;
        }

        String keyLower = keyText.toLowerCase();

        return keyLower.contains("meta")
                || keyLower.contains("windows")
                || keyLower.equals("win")
                || keyLower.contains("win");
    }

    // =====================================================
    // INICIAR SESIÓN
    // =====================================================

    private void startSession() {

        sessionActive = true;

        long sessionMillis = configManager.getTiempoSesionMillis();

        MensajeHelper.info(
                "Sesión iniciada. Duración: "
                        + sessionMillis / 1000
                        + " segundos");

        // Cancelar timer anterior
        if (sessionTimer != null) {

            sessionTimer.cancel();
        }

        sessionTimer = new Timer(true);

        sessionTimer.schedule(
                new TimerTask() {

                    @Override
                    public void run() {

                        sessionActive = false;

                        // Cerrar también la sesión
                        // guardada en Preferences
                        configManager.cerrarSesion();

                        MensajeHelper.info(
                                "Sesión expirada");

                        showNotification(
                                "Sesión expirada. "
                                        + "Ingrese PIN nuevamente.");
                    }

                },
                sessionMillis);
    }

    // =====================================================
    // CANCELAR SESIÓN
    // =====================================================

    public void cancelSession() {

        sessionActive = false;

        if (sessionTimer != null) {

            sessionTimer.cancel();

            sessionTimer = null;
        }

        configManager.cerrarSesion();

        pinBuffer.setLength(0);

        updatePinBuffer.setLength(0);

        updateMode = false;

        MensajeHelper.info(
                "Sesión cancelada manualmente");
    }

    // =====================================================
    // NOTIFICACIÓN
    // =====================================================

    private void showNotification(
            String message) {

        MensajeHelper.info(
                "Notificación: " + message);
    }

    // =====================================================
    // MOSTRAR ESTADO
    // =====================================================

    public void printStatus() {

        MensajeHelper.info(
                "Estado - Escuchando: "
                        + listening);

        MensajeHelper.info(
                "Estado - Sesión activa: "
                        + sessionActive);

        MensajeHelper.info(
                "Estado - Empleado: "
                        + configManager.getNombreCompletoEmpleado());

        MensajeHelper.info(
                "Estado - DNI: "
                        + configManager.getDniEmpleado());
    }
}