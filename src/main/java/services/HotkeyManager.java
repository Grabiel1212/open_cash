package services;

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

    public boolean isListening() {
        return listening;
    }

    public HotkeyManager(ConfigManagerDB configManager, Runnable openDrawerCallback) {
        this.configManager = configManager;
        this.openDrawerCallback = openDrawerCallback;

        // Silenciar logs molestos de jnativehook
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
    }

    public void startListening() {
        if (listening) {
            return;
        }

        try {
            // Verificar si el hook ya está registrado
            if (!GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.registerNativeHook();
            }
            GlobalScreen.addNativeKeyListener(this);
            listening = true;
            MensajeHelper.info("Escucha global activada (Ctrl + PIN + Win)");
        } catch (NativeHookException ex) {
            MensajeHelper.error("No se pudo registrar hook global: " + ex.getMessage(), ex);
        }
    }

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
            MensajeHelper.info("Escucha global detenida");
        } catch (Exception ex) {
            MensajeHelper.error("No se pudo detener la escucha: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        int code = e.getKeyCode();
        String keyText = NativeKeyEvent.getKeyText(code);
        ctrlDown = (e.getModifiers() & NativeKeyEvent.CTRL_MASK) != 0;
        altDown = (e.getModifiers() & NativeKeyEvent.ALT_MASK) != 0;

        // Detectar Ctrl+X para cerrar sesión
        if (ctrlDown && code == NativeKeyEvent.VC_X) {
            cancelSession();
            return;
        }

        // Detectar doble clic en Alt mientras se mantiene Ctrl presionado
        if (ctrlDown && code == NativeKeyEvent.VC_ALT) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastAltPressTime < 500) { // 500ms para doble clic
                altPressCount++;
                if (altPressCount == 2) {
                    updateMode = true;
                    updatePinBuffer.setLength(0);
                    MensajeHelper.info("Modo actualización de PIN activado");
                    showNotification("Modo actualización PIN activado. Ingrese nuevo PIN y presione Windows.");
                    altPressCount = 0;
                }
            } else {
                altPressCount = 1;
            }
            lastAltPressTime = currentTime;
            return;
        }

        // Evitar eventos duplicados de tecla Windows
        if (isWindowsKey(keyText)) {
            if (windowsKeyDown) {
                return;
            }
            windowsKeyDown = true;
        }

        if (updateMode) {
            // Modo actualización de PIN - capturar cualquier carácter imprimible
            if (keyText != null && keyText.length() == 1 && !Character.isISOControl(keyText.charAt(0))) {
                updatePinBuffer.append(keyText.charAt(0));
                System.out.print("*"); // feedback en consola
                return;
            }

            // Finalizar actualización con tecla Windows
            if (isWindowsKey(keyText)) {
                String newPin = updatePinBuffer.toString();
                if (newPin.length() >= 2) { // Validar longitud mínima (>= 2 caracteres)
                    if (configManager.actualizarPin(newPin)) {
                        MensajeHelper.info("PIN actualizado exitosamente");

                        // ✅ Abrir caja inmediatamente, pero SIN registrar apertura ni sesión
                        if (openDrawerCallback != null) {
                            Platform.runLater(openDrawerCallback);
                        }

                        // 🔒 Cerrar sesión activa siempre que se actualice el PIN
                        cancelSession();
                        MensajeHelper.info("Sesión cerrada automáticamente tras actualización de PIN");

                    } else {
                        MensajeHelper.advertencia("Error al actualizar PIN");
                    }
                } else {
                    MensajeHelper.advertencia("PIN demasiado corto. Mínimo 2 caracteres");
                }
                updateMode = false;
                updatePinBuffer.setLength(0);
            }

        } else if (ctrlDown) {
            // Modo normal de apertura de caja
            // Capturar caracteres imprimibles (no solo números)
            if (keyText != null && keyText.length() == 1 && !Character.isISOControl(keyText.charAt(0))) {
                pinBuffer.append(keyText.charAt(0));
                System.out.print("*"); // feedback en consola
                return;
            }

            // Detectar tecla Windows/Meta como finalizador del PIN
            if (isWindowsKey(keyText)) {
                if (sessionActive) {
                    if (openDrawerCallback != null) {
                        Platform.runLater(openDrawerCallback);
                    }
                    MensajeHelper.info("Caja abierta mediante Ctrl+Windows (sesión activa)");
                } else {
                    // Validar PIN ingresado
                    String enteredPin = pinBuffer.toString();
                    if (configManager.validarPin(enteredPin)) {
                        MensajeHelper.info("PIN correcto: " + enteredPin);
                        if (openDrawerCallback != null) {
                            Platform.runLater(openDrawerCallback);
                        }
                        startSession();
                        showNotification("Sesión activada. Ahora basta Ctrl+Win para abrir.");
                    } else {
                        MensajeHelper.advertencia("PIN incorrecto: " + enteredPin);
                    }
                }
                pinBuffer.setLength(0); // limpiar buffer siempre
            }
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        int code = e.getKeyCode();
        String keyText = NativeKeyEvent.getKeyText(code);

        // Restablecer estado de tecla Windows cuando se suelta
        if (isWindowsKey(keyText)) {
            windowsKeyDown = false;
        }

        // Restablecer estado de tecla Alt cuando se suelta
        if (code == NativeKeyEvent.VC_ALT) {
            altDown = false;
        }

        // Restablecer estado de tecla Ctrl cuando se suelta
        if (code == NativeKeyEvent.VC_CONTROL) {
            ctrlDown = false;
            if (pinBuffer.length() > 0) {
                pinBuffer.setLength(0);
                MensajeHelper.info("Buffer limpiado (Ctrl liberado)");
            }
            if (updatePinBuffer.length() > 0) {
                updateMode = false;
                updatePinBuffer.setLength(0);
                MensajeHelper.info("Modo actualización cancelado (Ctrl liberado)");
            }
        }
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent nativeKeyEvent) {
        // No usado
    }

    private boolean isWindowsKey(String keyText) {
        if (keyText == null) {
            return false;
        }
        String keyLower = keyText.toLowerCase();
        return keyLower.contains("meta") || keyLower.contains("windows") || keyLower.contains("win");
    }

    private void startSession() {
        sessionActive = true;
        long sessionMillis = configManager.getTiempoSesionMillis();
        MensajeHelper.info("Sesión iniciada. Duración: " + sessionMillis / 1000 + " segundos");

        if (sessionTimer != null) {
            sessionTimer.cancel();
        }

        sessionTimer = new Timer(true);
        sessionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                sessionActive = false;
                MensajeHelper.info("Sesión expirada");
                showNotification("Sesión expirada. Ingrese PIN nuevamente.");
            }
        }, sessionMillis);
    }

    public void cancelSession() {
        sessionActive = false;
        if (sessionTimer != null) {
            sessionTimer.cancel();
            sessionTimer = null;
        }
        configManager.cerrarSesion();
        MensajeHelper.info("Sesión cancelada manualmente");
    }

    private void showNotification(String message) {
        MensajeHelper.info("Notificación: " + message);
        // Aquí puedes implementar notificaciones del sistema si es necesario
    }

    public void printStatus() {
        MensajeHelper.info("Estado - Escuchando: " + listening);
        MensajeHelper.info("Estado - Sesión activa: " + sessionActive);
        MensajeHelper.info("Estado - Cajero: " + configManager.getNombreCajero());
    }
}