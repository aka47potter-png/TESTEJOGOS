package io.github.some_example_name;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MpuClient {
    private static final long POLL_INTERVAL_MS = 100;
    private static final int REQUEST_TIMEOUT_MS = 700;
    private static final int CALIBRATION_TIMEOUT_MS = 120_000;

    private final String baseUrl;
    private final ScheduledExecutorService dataExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService calibrationExecutor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean calibrating = new AtomicBoolean();

    private volatile SensorData latestData = new SensorData(0, 0, 0, false);
    private volatile int calibrationProgress;
    private volatile String lastError;

    public MpuClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public void start() {
        dataExecutor.scheduleWithFixedDelay(() -> {
            try {
                JsonValue response = getJson("/api/dados");
                latestData = new SensorData(
                    response.getFloat("angX"),
                    response.getFloat("angY"),
                    response.getFloat("temp"),
                    response.getBoolean("ok")
                );
                lastError = null;
            } catch (Exception exception) {
                lastError = exception.getMessage();
            }
        }, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public SensorData getLatestData() {
        return latestData;
    }

    public int getCalibrationProgress() {
        return calibrationProgress;
    }

    public boolean isCalibrating() {
        return calibrating.get();
    }

    public String getLastError() {
        return lastError;
    }

    public void calibrate() {
        if (!calibrating.compareAndSet(false, true)) {
            return;
        }

        calibrationProgress = 0;
        calibrationExecutor.execute(() -> {
            try {
                getResponse("/api/calibrar");
                long deadline = System.currentTimeMillis() + CALIBRATION_TIMEOUT_MS;
                while (calibrationProgress < 100 && System.currentTimeMillis() < deadline) {
                    JsonValue response = getJson("/api/progresso");
                    calibrationProgress = Math.max(0, Math.min(100, response.getInt("progresso")));
                    if (calibrationProgress < 100) {
                        Thread.sleep(250);
                    }
                }
                if (calibrationProgress < 100) {
                    lastError = "Tempo limite aguardando a calibracao";
                }
            } catch (Exception exception) {
                lastError = exception.getMessage();
            } finally {
                calibrating.set(false);
            }
        });
    }

    public void dispose() {
        dataExecutor.shutdownNow();
        calibrationExecutor.shutdownNow();
    }

    private JsonValue getJson(String path) throws Exception {
        return new JsonReader().parse(getResponse(path));
    }

    private String getResponse(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(REQUEST_TIMEOUT_MS);
        connection.setReadTimeout(REQUEST_TIMEOUT_MS);

        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
            StringBuilder body = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                }
            }
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("HTTP " + status + " em " + path);
            }
            return body.toString();
        } finally {
            connection.disconnect();
        }
    }

    public static class SensorData {
        public final float angX;
        public final float angY;
        public final float temp;
        public final boolean ok;

        public SensorData(float angX, float angY, float temp, boolean ok) {
            this.angX = angX;
            this.angY = angY;
            this.temp = temp;
            this.ok = ok;
        }
    }
}