# NovaStream Studio

Aplicativo Android nativo em Kotlin + Jetpack Compose, com motor RootEncoder.

## Versão 0.8.0

### Funções disponíveis no código
- Câmera frontal/traseira, prévia OpenGL e gravação MP4.
- Transmissão direta por RTMP/RTMPS e SRT.
- Captura de tela via MediaProjection com áudio interno permitido pelo Android + microfone.
- Composição OpenGL compartilhada entre prévia e encoder.
- Cenas básicas e criação/salvamento local dos nomes de cenas personalizadas (cenas customizadas usam a composição de espera).
- Até cinco fontes de imagem simultâneas e três textos simultâneos.
- QR Code para Pix, site, redes sociais ou qualquer texto/URL.
- Chroma key por shader GPU, filtros de câmera e PNGTuber 2D por detecção de voz.
- Controle de mute e ganhos de microfone e áudio interno durante captura de tela.
- Preferências de produção salvas no aparelho.
- Bitrate selecionado antes da live aplicado ao conectar; alteração em tempo real durante a live.

### Relay para multistream
A pasta `relay/` contém exemplo de distribuição RTMP com MediaMTX + FFmpeg para até seis destinos. Exige VPS, segurança, URLs/chaves válidas e testes. Não é login integrado e não é recurso standalone do APK.

### Recursos ainda não implementados
- Login OAuth para YouTube/Twitch/Facebook ou autorização TikTok.
- Chat unificado autenticado e moderação.
- Widgets web na composição.
- Reprodução de vídeos, música e playlists como fontes.
- Alertas/doações em tempo real e biblioteca de stickers/efeitos.
- Avatar 3D VRM e rastreamento facial/gestual.
- Editor avançado com camadas arrastáveis, duplicação e layouts independentes por cena.
- Multistream sem relay e replay instantâneo.

Esses itens são pendências, não funções prontas.

## Build
GitHub Actions executa `gradle assembleDebug` e publica `NovaStreamStudio-APK`.

## Dependências
- RootEncoder (Apache-2.0): https://github.com/pedroSG94/RootEncoder
- ZXing Core (Apache-2.0): https://github.com/zxing/zxing
