package io.github.some_example_name;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

public class Main implements ApplicationListener {
    SpriteBatch spriteBatch;
    FitViewport viewport;
    BitmapFont font;
    Texture buttonTexture;
    ShapeRenderer shapeRenderer;
    Vector2 touchPos;
    MpuClient mpuClient;
    float sensorX = 1.25f;
    float sensorY = 3.85f;

    Button botaoJogar;
    Button botaoCalibrar;
    Button botaoConfig;
    Button botaoSair;

    @Override
    public void create() {
        spriteBatch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        viewport = new FitViewport(8, 5);

        font = new BitmapFont();
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.02f);

        buttonTexture = new Texture("button.png");
        touchPos = new Vector2();
        String baseUrl = System.getProperty("mpu.baseUrl",
            System.getenv().getOrDefault("MPU_BASE_URL", "http://192.168.4.1"));
        mpuClient = new MpuClient(baseUrl);
        mpuClient.start();

        botaoJogar = new Button(buttonTexture, font, "Jogar", 2.5f, 3.0f, 3, 0.7f);
        botaoCalibrar = new Button(buttonTexture, font, "Calibrar sensor", 2.5f, 2.1f, 3, 0.7f);
        botaoConfig = new Button(buttonTexture, font, "Configuracoes", 2.5f, 1.2f, 3, 0.7f);
        botaoSair = new Button(buttonTexture, font, "Sair", 2.5f, 0.3f, 3, 0.7f);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void render() {
        input();
        logic();
        draw();
    }

    private void input() {
        if (Gdx.input.justTouched()) {
            touchPos.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(touchPos);

            if (botaoJogar.foiClicado(touchPos.x, touchPos.y)) {
                Gdx.app.log("RehabCar", "Jogar");
            }
            if (botaoCalibrar.foiClicado(touchPos.x, touchPos.y)) {
                mpuClient.calibrate();
            }
            if (botaoConfig.foiClicado(touchPos.x, touchPos.y)) {
                Gdx.app.log("RehabCar", "Configuracoes");
            }
            if (botaoSair.foiClicado(touchPos.x, touchPos.y)) {
                Gdx.app.exit();
            }
        }
    }

    private void logic() {
        MpuClient.SensorData data = mpuClient.getLatestData();
        sensorX = 1.25f + clamp(data.angY / 45f) * 0.7f;
        sensorY = 3.85f - clamp(data.angX / 45f) * 0.6f;
    }

    private void draw() {
        ScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f);
        viewport.apply();
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);

        spriteBatch.begin();
        font.draw(spriteBatch, "RehabCar", 0, 4.8f, viewport.getWorldWidth(), Align.center, false);
        botaoJogar.draw(spriteBatch);
        botaoCalibrar.draw(spriteBatch);
        botaoConfig.draw(spriteBatch);
        botaoSair.draw(spriteBatch);
        MpuClient.SensorData data = mpuClient.getLatestData();
        font.draw(spriteBatch, "MPU " + (data.ok ? "conectado" : "sem conexao"), 0.35f, 2.95f);
        font.draw(spriteBatch, "X " + (int) data.angX + "  Y " + (int) data.angY + "  T " + (int) data.temp + " C", 0.35f, 2.65f);
        if (mpuClient.isCalibrating()) {
            font.draw(spriteBatch, "Calibrando " + mpuClient.getCalibrationProgress() + "%", 0.35f, 2.35f);
        }
        spriteBatch.end();

        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.45f, 0.48f, 0.52f, 1f);
        shapeRenderer.rect(0.35f, 3.15f, 1.8f, 1.4f);
        shapeRenderer.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.2f, 0.85f, 0.55f, 1f);
        shapeRenderer.circle(sensorX, sensorY, 0.12f);
        shapeRenderer.end();
    }

    private float clamp(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        spriteBatch.dispose();
        shapeRenderer.dispose();
        font.dispose();
        buttonTexture.dispose();
        mpuClient.dispose();
    }
}