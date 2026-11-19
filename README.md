# NovaStream Studio

Aplicativo Android nativo em Kotlin + Jetpack Compose com motor de mídia RootEncoder.

## Versão 0.7.0

### Recursos implementados
- Estúdio móvel dividido em Ao Vivo, Cenas, Fontes, Áudio e Ajustes.
- Câmera frontal e traseira com prévia OpenGL.
- Transmissão RTMP, RTMPS e SRT.
- Gravação MP4 pelo pipeline de mídia.
- Captura da tela Android usando MediaProjection e serviço em primeiro plano.
- No Android 10+, mistura de áudio interno permitido pelo sistema com microfone.
- Controles separados de ganho de microfone e áudio interno durante o modo de tela.
- Serviço em primeiro plano para manter a live de câmera e microfone ao alternar para outro app.
- Composição OpenGL com cenas de câmera / intervalo / tela preta.
- Sobreposição de texto e imagem/logotipo no vídeo codificado.
- Filtro Chroma Key GPU que remove verde da câmera e substitui pelo fundo escolhido.
- Filtros GPU de beleza, sépia, preto e branco e saturação.
- PNGTuber 2D com imagens de boca aberta/fechada e deteção de voz no microfone.
- Controle de mute, ganho de microfone, bitrate e estado de conexão.

### Multistream por relay
O app envia um destino RTMP/SRT por conexão. Para replicar a mesma transmissão para até seis destinos, o projeto inclui uma infraestrutura opcional em `/relay` baseada em MediaMTX + FFmpeg. É necessário um VPS/Linux com Docker e as URLs/chaves de transmissão de cada plataforma. Consulte `relay/README.md`.

### Limitações atuais
- Ainda não tem login OAuth de plataformas, chat unificado, VTuber/VRM, rastreamento facial, widgets web, playlists de vídeo/música ou alertas em tempo real.
- A tela de captura deve ser autorizada pelo usuário a cada sessão.
- O áudio interno pode não ser capturável quando o app de origem bloqueia AudioPlaybackCapture.
- A prévia da tela é ocultada durante o compartilhamento para evitar espelho infinito. Autorize a captura, inicie a live e depois abra o jogo/app.
- Gravações de teste ficam na pasta privada do aplicativo.
- O APK do workflow é uma build de depuração, não uma versão assinada para Play Store.

## Compilação
GitHub Actions executa `gradle assembleDebug` e publica o artefato `NovaStreamStudio-APK`.

## Motor de mídia
RootEncoder (Apache License 2.0): https://github.com/pedroSG94/RootEncoder
