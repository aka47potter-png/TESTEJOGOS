# ESP32 + MPU6050

Este diretorio e reservado ao firmware do ESP32 que le o sensor MPU6050 e disponibiliza os dados para o jogo via Wi-Fi HTTP.

Endpoints esperados pelo cliente LibGDX:

- `GET /api/dados`: JSON com `angX`, `angY`, `temp` e `ok`.
- `GET /api/calibrar`: inicia a calibracao do sensor.
- `GET /api/progresso`: JSON com o campo `progresso` (0 a 100).
