package io.github.some_example_name;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class MpuClient implements AutoCloseable {
    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final int READ_TIMEOUT_MS = 1500;
    private static final int MAX_RESPONSE_CHARS = 65536;
    private static final long PROGRESS_INTERVAL_MS = 100;

    private final String baseUrl;
    private final ExecutorService executor;
    private final AtomicBoolean telemetryRequestInFlight = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Telemetry> latestTelemetry = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicReference<CalibrationStatus> calibrationStatus =
            new AtomicReference<>(CalibrationStatus.IDLE);
    private volatile int calibrationProgress;
    private Future<?> calibrationTask;

    public MpuClient(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("O endereço do ESP32 não pode ficar vazio.");
        }

        String normalizedUrl = baseUrl.trim();
        URI uri;
        try {
            uri = URI.create(normalizedUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Endereço do ESP32 inválido.", exception);
        }
        String scheme = uri.getScheme();
        if (uri.getHost() == null || scheme == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Use um endereço HTTP ou HTTPS válido para o ESP32.");
        }

        while (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }
        this.baseUrl = normalizedUrl;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mpu-http-client");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void requestTelemetry() {
        if (closed.get() || !telemetryRequestInFlight.compareAndSet(false, true)) {
            return;
        }

        try {
            executor.execute(() -> {
                try {
                    Telemetry telemetry = readTelemetry(getJson("/api/dados"));
                    latestTelemetry.set(telemetry);
                    lastError.set(telemetry.isOk() ? null : "O ESP32 informou que os dados não estão disponíveis.");
                } catch (IOException | RuntimeException exception) {
                    lastError.set(messageFor(exception));
                } finally {
                    telemetryRequestInFlight.set(false);
                }
            });
        } catch (RejectedExecutionException exception) {
            telemetryRequestInFlight.set(false);
        }
    }

    public synchronized boolean startCalibration() {
        CalibrationStatus currentStatus = calibrationStatus.get();
        if (closed.get() || currentStatus == CalibrationStatus.STARTING
                || currentStatus == CalibrationStatus.RUNNING) {
            return false;
        }

        calibrationProgress = 0;
        lastError.set(null);
        calibrationStatus.set(CalibrationStatus.STARTING);
        try {
            calibrationTask = executor.submit(this::runCalibration);
            return true;
        } catch (RejectedExecutionException exception) {
            calibrationStatus.set(CalibrationStatus.FAILED);
            lastError.set("Não foi possível iniciar a comunicação com o ESP32.");
            return false;
        }
    }

    private void runCalibration() {
        try {
            getResponse("/api/calibrar");
            if (calibrationStatus.get() != CalibrationStatus.STARTING) {
                return;
            }
            calibrationStatus.set(CalibrationStatus.RUNNING);

            while (!closed.get() && calibrationStatus.get() == CalibrationStatus.RUNNING) {
                Thread.sleep(PROGRESS_INTERVAL_MS);
                if (calibrationStatus.get() != CalibrationStatus.RUNNING) {
                    break;
                }
                try {
                    int progress = readProgress(getJson("/api/progresso"));
                    calibrationProgress = progress;
                    lastError.set(null);
                    if (progress >= 100) {
                        calibrationStatus.set(CalibrationStatus.COMPLETED);
                    }
                } catch (IOException | RuntimeException exception) {
                    lastError.set(messageFor(exception));
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException exception) {
            if (!closed.get() && calibrationStatus.get() != CalibrationStatus.IDLE) {
                lastError.set(messageFor(exception));
                calibrationStatus.set(CalibrationStatus.FAILED);
            }
        }
    }

    public synchronized void cancelCalibration() {
        CalibrationStatus currentStatus = calibrationStatus.get();
        if (currentStatus == CalibrationStatus.STARTING || currentStatus == CalibrationStatus.RUNNING) {
            calibrationStatus.set(CalibrationStatus.IDLE);
            if (calibrationTask != null) {
                calibrationTask.cancel(true);
            }
        }
    }

    private JsonValue getJson(String path) throws IOException {
        String responseBody = getResponse(path);
        if (responseBody.trim().isEmpty()) {
            throw new IllegalArgumentException("O ESP32 retornou uma resposta vazia.");
        }
        JsonValue json = new JsonReader().parse(responseBody);
        if (!json.isObject()) {
            throw new IllegalArgumentException("Resposta JSON inválida do ESP32.");
        }
        return json;
    }

    private String getResponse(String path) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            InputStream responseStream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseBody = responseStream == null ? "" : readResponse(responseStream);
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("ESP32 respondeu HTTP " + responseCode + ".");
            }
            return responseBody;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readResponse(InputStream inputStream) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[2048];
            int charsRead;
            while ((charsRead = reader.read(buffer)) != -1) {
                if (response.length() + charsRead > MAX_RESPONSE_CHARS) {
                    throw new IOException("Resposta do ESP32 excedeu o tamanho permitido.");
                }
                response.append(buffer, 0, charsRead);
            }
        }
        return response.toString();
    }

    private Telemetry readTelemetry(JsonValue json) {
        if (!json.has("angX") || !json.has("angY") || !json.has("temp")) {
            throw new IllegalArgumentException("Resposta de telemetria incompleta.");
        }
        float angX = json.getFloat("angX");
        float angY = json.getFloat("angY");
        float temperature = json.getFloat("temp");
        if (!isFinite(angX) || !isFinite(angY) || !isFinite(temperature)) {
            throw new IllegalArgumentException("A telemetria contém valores numéricos inválidos.");
        }
        return new Telemetry(angX, angY, temperature, json.getBoolean("ok", false));
    }

    private int readProgress(JsonValue json) {
        if (!json.has("progresso")) {
            throw new IllegalArgumentException("Resposta de progresso incompleta.");
        }
        return Math.max(0, Math.min(100, json.getInt("progresso")));
    }

    private String messageFor(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Falha de comunicação com o ESP32." : message;
    }

    private boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    public Telemetry getLatestTelemetry() {
        return latestTelemetry.get();
    }

    public String getLastError() {
        return lastError.get();
    }

    public int getCalibrationProgress() {
        return calibrationProgress;
    }

    public CalibrationStatus getCalibrationStatus() {
        return calibrationStatus.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            calibrationStatus.set(CalibrationStatus.IDLE);
            executor.shutdownNow();
        }
    }

    public enum CalibrationStatus {
        IDLE,
        STARTING,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public static final class Telemetry {
        private final float angX;
        private final float angY;
        private final float temperature;
        private final boolean ok;

        private Telemetry(float angX, float angY, float temperature, boolean ok) {
            this.angX = angX;
            this.angY = angY;
            this.temperature = temperature;
            this.ok = ok;
        }

        public float getAngX() {
            return angX;
        }

        public float getAngY() {
            return angY;
        }

        public float getTemperature() {
            return temperature;
        }

        public boolean isOk() {
            return ok;
        }
    }
}