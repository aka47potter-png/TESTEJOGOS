# projtec_ardui

A [libGDX](https://libgdx.com/) project generated with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff).

This project was generated with a template including simple application launchers and an `ApplicationAdapter` extension that draws libGDX logo.

## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3; was called 'desktop' in older docs.

## Gradle

This project uses [Gradle](https://gradle.org/) to manage dependencies.
The Gradle wrapper was included, so you can run Gradle tasks using `gradlew.bat` or `./gradlew` commands.
Useful Gradle tasks and flags:

- `--continue`: when using this flag, errors will not stop the tasks from running.
- `--daemon`: when using this flag, Gradle daemon will be used to run chosen tasks.
- `--offline`: when using this flag, cached dependency archives will be used.
- `--refresh-dependencies`: this flag forces validation of all dependencies. Useful for snapshot versions.
- `build`: builds sources and archives of every project.
- `cleanEclipse`: removes Eclipse project data.
- `cleanIdea`: removes IntelliJ project data.
- `clean`: removes `build` folders, which store compiled classes and built archives.
- `eclipse`: generates Eclipse project data.
- `idea`: generates IDEA project data.
- `lwjgl3:jar`: builds application's runnable jar, which can be found at `lwjgl3/build/libs`.
- `lwjgl3:run`: starts the application.
- `test`: runs unit tests (if any).

Note that most tasks that are not specific to a project can be run with `name:` prefix, where the `name` should be replaced with the ID of a specific project.
For example, `core:clean` removes `build` folder only for the core project.

## Sensor MPU6050 via ESP32

O cliente consulta `GET /api/dados` a cada 100 ms em uma thread de fundo. O marcador no menu acompanha `angX` e `angY`; o botao "Calibrar sensor" chama `/api/calibrar` e acompanha `/api/progresso` sem bloquear a renderizacao.

Por padrao, a aplicacao usa `http://192.168.4.1`. Para informar o endereco do ESP32, configure a variavel de ambiente ao iniciar o jogo:

```bash
MPU_BASE_URL=http://192.168.1.50 ./gradlew lwjgl3:run
```

Substitua o IP pelo endereco alcancavel na rede local.

## 📊 Acompanhamento do Projeto

O desenvolvimento do projeto é acompanhado através do [GitHub Projects](https://github.com/bragatto-tec/proj_gametec/projects). O gerenciamento visual, as milestones, as issues e as tarefas são mantidos no GitHub Projects oficial.

- 🎮 Gameplay
- 🔌 Integração IoT
- 🦵 Reabilitação do tornozelo
- 📊 Métricas e pontuação
- 🧪 Testes e documentação

**Project:** [Projeto Tec — Reabilitação do Tornozelo](https://github.com/bragatto-tec/proj_gametec/projects)
