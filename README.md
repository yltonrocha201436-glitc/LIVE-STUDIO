# NovaStream Studio

Aplicativo Android nativo em Kotlin + Jetpack Compose, com motor RootEncoder.

## Versão 0.9.0

### Funções presentes no código
- Câmera frontal/traseira, prévia OpenGL e gravação MP4.
- Transmissão por RTMP/RTMPS e SRT para um destino configurado.
- Captura de tela via MediaProjection com áudio interno permitido pelo Android + microfone.
- Composição OpenGL compartilhada entre prévia e encoder.
- Cenas básicas e criação, seleção e exclusão de cenas personalizadas.
- Configurações próprias por cena: três textos, filtro, chroma key, sensibilidade e fontes de imagem.
- Até cinco imagens por cena, persistidas no armazenamento privado do aplicativo.
- Controles de posição horizontal, posição vertical e tamanho para cada imagem.
- QR Code para Pix, site, redes sociais ou texto, adicionado à cena atual.
- Chroma key por shader GPU, filtros de câmera e PNGTuber 2D por detecção de voz.
- Controles de mute e ganho de microfone e áudio interno durante captura de tela.
- Preferências de produção salvas localmente.
- Bitrate selecionado antes da live aplicado ao conectar; alteração em tempo real durante a live.

### Limitações atuais
- Não há login OAuth integrado para YouTube, Twitch, Facebook ou TikTok.
- Não há chat unificado autenticado, moderação ou alertas oficiais em tempo real.
- Não há fonte de vídeo/playlist nem navegador Web como camada de mídia.
- O editor ajusta posição e tamanho das imagens por controles deslizantes; ainda não permite arrastar camadas diretamente na prévia.
- As cenas personalizadas ainda usam fundo de espera, não fontes de câmera/vídeo independentes e transições avançadas.
- Não há avatar 3D VRM nem rastreamento facial/gestual.
- Multistream depende do relay externo descrito abaixo; não há envio nativo simultâneo a várias plataformas.
- Não há replay buffer.

### Relay para multistream
A pasta `relay/` contém exemplo de distribuição RTMP com MediaMTX + FFmpeg para até seis destinos. Exige VPS, configuração e chaves válidas, segurança e testes. Não é login integrado nem função standalone do APK.

## Build e instalação
O GitHub Actions executa `gradle assembleDebug` a cada push em `main` e publica o APK de depuração como artefato `NovaStreamStudio-APK`. O APK debug não é uma versão assinada para publicação na Play Store.

## Dependências
- RootEncoder (Apache-2.0): https://github.com/pedroSG94/RootEncoder
- ZXing Core (Apache-2.0): https://github.com/zxing/zxing
