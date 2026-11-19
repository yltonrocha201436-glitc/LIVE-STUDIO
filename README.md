# NovaStream Studio

Aplicativo Android nativo em Kotlin + Jetpack Compose, com motor RootEncoder.

## Versão 0.9.0

### Funções presentes no código
- Câmera frontal/traseira, prévia OpenGL e gravação MP4.
- Transmissão por RTMP/RTMPS e SRT para um destino configurado.
- Captura de tela via MediaProjection com áudio interno permitido pelo Android + microfone.
- Composição OpenGL compartilhada entre prévia e encoder.
- Cenas básicas e criação, seleção e exclusão de cenas personalizadas.
- Configurações próprias por cena: três textos, filtro, chroma key, sensibilidade, imagens e vídeo de fundo.
- Até cinco imagens por cena, persistidas no armazenamento privado do aplicativo.
- Controles de posição e tamanho por controles deslizantes, além de arraste da imagem selecionada diretamente na prévia.
- QR Code para Pix, site, redes sociais ou texto, adicionado à cena atual.
- Uma fonte de vídeo em tela cheia por cena, com reprodução em loop e gravação/transmissão pelo encoder.
- Chroma key por shader GPU, filtros de câmera e PNGTuber 2D por detecção de voz.
- Controles de mute e ganho de microfone e áudio interno durante captura de tela.
- Preferências de produção salvas localmente.
- Bitrate selecionado antes da live aplicado ao conectar; alteração em tempo real durante a live.

### Limitações atuais
- O áudio do arquivo de vídeo não é mixado. O microfone permanece como fonte de áudio.
- Não há playlist de vídeos nem múltiplas fontes de vídeo simultâneas.
- Não há navegador Web como camada de mídia.
- Não há login OAuth integrado para YouTube, Twitch, Facebook ou TikTok.
- Não há chat unificado autenticado, moderação ou alertas oficiais em tempo real.
- Não há avatar 3D VRM nem rastreamento facial/gestual.
- As cenas personalizadas usam fundo de espera quando não têm vídeo selecionado. Ainda faltam fontes de câmera independentes por cena e transições avançadas.
- Multistream depende do relay externo descrito abaixo; não há envio nativo simultâneo a várias plataformas.
- Não há replay buffer.

### Relay para multistream
A pasta `relay/` contém exemplo de distribuição RTMP com MediaMTX + FFmpeg para até seis destinos. Exige VPS, configuração e chaves válidas, segurança e testes. Não é login integrado nem função standalone do APK.

## Build e instalação
O GitHub Actions executa `gradle assembleDebug` a cada push em `main` e publica o APK de depuração como artefato `NovaStreamStudio-APK`. O APK debug pode ser instalado por sideload em aparelho compatível, mas não é uma versão assinada para publicação na Play Store.

A compilação automatizada foi concluída. A validação funcional em aparelhos Android físicos ainda precisa ser feita.

## Dependências
- RootEncoder (Apache-2.0): https://github.com/pedroSG94/RootEncoder
- ZXing Core (Apache-2.0): https://github.com/zxing/zxing
