package io.github.some_example_name;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.util.Locale;

public class Main implements ApplicationListener {
    private static final float TELEMETRY_INTERVAL_SECONDS = 0.1f;
    private static final float MAX_TILT_DEGREES = 45f;

    private SpriteBatch spriteBatch;
    private FitViewport viewport;
    private BitmapFont font;
    private Texture buttonTexture;
    private Texture pixelTexture;
    private Texture playerTexture;
    private Stage stage;
    private MpuClient mpuClient;
    private Table uiTable;
    private Label statusLabel;
    private Label progressLabel;
    private ProgressBar progressBar;
    private TextButton calibrateButton;
    private TextButton returnButton;
    private Mode mode = Mode.GAME;
    private float telemetryTimer;
    private float playerX = 4f;
    private float playerY = 2.1f;

    @Override
    public void create() {
        spriteBatch = new SpriteBatch();
        viewport = new FitViewport(8, 5);
        stage = new Stage(new FitViewport(8, 5));
        font = new BitmapFont();
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.02f);
        buttonTexture = new Texture("button.png");
        pixelTexture = createPixelTexture();
        playerTexture = createPlayerTexture();
        mpuClient = new MpuClient(System.getProperty("mpu.baseUrl", "http://192.168.4.1"));
        createInterface();
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        updateGame(delta);
        updateInterface();

        ScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f);
        viewport.apply();
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.begin();
        if (mode == Mode.GAME) {
            spriteBatch.draw(playerTexture, playerX - 0.22f, playerY - 0.22f, 0.44f, 0.44f);
        }
        spriteBatch.end();

        stage.act(delta);
        stage.draw();
    }

    private void createInterface() {
        TextureRegionDrawable buttonDrawable = new TextureRegionDrawable(new TextureRegion(buttonTexture));
        TextureRegionDrawable pixelDrawable = new TextureRegionDrawable(new TextureRegion(pixelTexture));

        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = buttonDrawable;
        buttonStyle.down = buttonDrawable.tint(new Color(0.72f, 0.82f, 0.84f, 1f));
        buttonStyle.font = font;
        buttonStyle.fontColor = Color.WHITE;

        ProgressBar.ProgressBarStyle progressStyle = new ProgressBar.ProgressBarStyle();
        progressStyle.background = pixelDrawable.tint(new Color(0.18f, 0.24f, 0.27f, 1f));
        progressStyle.knobBefore = pixelDrawable.tint(new Color(0.2f, 0.78f, 0.62f, 1f));
        progressBar = new ProgressBar(0f, 100f, 1f, false, progressStyle);
        progressBar.setAnimateDuration(0.08f);
        progressBar.setVisible(false);

        statusLabel = new Label("Conectando ao sensor...", labelStyle);
        statusLabel.setAlignment(com.badlogic.gdx.utils.Align.center);
        progressLabel = new Label("Iniciando calibracao...", labelStyle);
        progressLabel.setAlignment(com.badlogic.gdx.utils.Align.center);

        calibrateButton = new TextButton("Calibrar Sensor", buttonStyle);
        calibrateButton.addListener(new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                if (mode == Mode.GAME) {
                    mode = Mode.CALIBRATION;
                    mpuClient.startCalibration();
                }
            }
        });

        returnButton = new TextButton("Voltar ao jogo", buttonStyle);
        returnButton.setVisible(false);
        returnButton.addListener(new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                if (mode == Mode.CALIBRATION) {
                    mpuClient.cancelCalibration();
                    mode = Mode.GAME;
                }
            }
        });

        uiTable = new Table();
        uiTable.setFillParent(true);
        uiTable.top().padTop(0.16f);
        uiTable.add(new Label("RehabCar", labelStyle)).padBottom(0.08f).row();
        uiTable.add(statusLabel).width(7.5f).padBottom(0.1f).row();
        uiTable.add(progressLabel).padBottom(0.08f).row();
        uiTable.add(progressBar).width(5f).height(0.3f).padBottom(0.12f).row();
        uiTable.add(calibrateButton).width(2.7f).height(0.55f).padBottom(0.08f).row();
        uiTable.add(returnButton).width(2.7f).height(0.55f).row();
        stage.addActor(uiTable);
    }

    private void updateGame(float delta) {
        if (mode == Mode.GAME) {
            telemetryTimer += delta;
            if (telemetryTimer >= TELEMETRY_INTERVAL_SECONDS) {
                telemetryTimer %= TELEMETRY_INTERVAL_SECONDS;
                mpuClient.requestTelemetry();
            }

            MpuClient.Telemetry telemetry = mpuClient.getLatestTelemetry();
            if (telemetry != null && telemetry.isOk()) {
                float targetX = 4f + MathUtils.clamp(telemetry.getAngX() / MAX_TILT_DEGREES, -1f, 1f) * 3.2f;
                float targetY = 2.1f + MathUtils.clamp(telemetry.getAngY() / MAX_TILT_DEGREES, -1f, 1f) * 1.55f;
                float smoothing = Math.min(1f, delta * 8f);
                playerX = MathUtils.lerp(playerX, targetX, smoothing);
                playerY = MathUtils.lerp(playerY, targetY, smoothing);
            }
        } else if (mpuClient.getCalibrationStatus() == MpuClient.CalibrationStatus.COMPLETED) {
            mode = Mode.GAME;
        }
    }

    private void updateInterface() {
        boolean calibrating = mode == Mode.CALIBRATION;
        calibrateButton.setVisible(!calibrating);
        returnButton.setVisible(calibrating);
        progressBar.setVisible(calibrating);
        progressLabel.setVisible(calibrating);

        if (calibrating) {
            MpuClient.CalibrationStatus status = mpuClient.getCalibrationStatus();
            if (status == MpuClient.CalibrationStatus.STARTING) {
                progressLabel.setText("Iniciando calibracao...");
            } else {
                progressLabel.setText("Calibrando... " + mpuClient.getCalibrationProgress() + "%");
            }
            progressBar.setValue(mpuClient.getCalibrationProgress());
            String error = mpuClient.getLastError();
            statusLabel.setText(error == null ? "Mantenha o sensor parado durante a calibracao." : error);
        } else {
            MpuClient.Telemetry telemetry = mpuClient.getLatestTelemetry();
            if (telemetry == null) {
                statusLabel.setText("Conectando ao sensor...");
            } else if (telemetry.isOk()) {
                statusLabel.setText(String.format(Locale.ROOT,
                        "X: %.1f  Y: %.1f  Temperatura: %.1f C",
                        telemetry.getAngX(), telemetry.getAngY(), telemetry.getTemperature()));
            } else {
                statusLabel.setText("Dados do sensor indisponiveis.");
            }
            String error = mpuClient.getLastError();
            if (error != null) {
                statusLabel.setText("Sem conexao com o ESP32: " + error);
            }
        }
    }

    private Texture createPixelTexture() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private Texture createPlayerTexture() {
        Pixmap pixmap = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        pixmap.setColor(0.2f, 0.78f, 0.62f, 1f);
        pixmap.fillCircle(16, 16, 15);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        mpuClient.close();
        stage.dispose();
        spriteBatch.dispose();
        font.dispose();
        buttonTexture.dispose();
        pixelTexture.dispose();
        playerTexture.dispose();
    }

    private enum Mode {
        GAME,
        CALIBRATION
    }
}