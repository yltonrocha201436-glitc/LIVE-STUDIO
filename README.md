# NovaStream Studio

Aplicativo Android nativo em Kotlin + Jetpack Compose com motor de mídia RootEncoder.

## Versão 0.6.0

### Recursos implementados no código
- Estúdio móvel dividido em Ao Vivo, Cenas, Fontes, Áudio e Ajustes.
- Câmera frontal e traseira com prévia OpenGL.
- Transmissão RTMP, RTMPS e SRT para um destino por vez.
- Gravação MP4 pelo pipeline de mídia.
- Captura da tela Android usando MediaProjection e serviço em primeiro plano.
- No Android 10+, mistura de áudio interno permitido pelo sistema com microfone.
- Controles separados de ganho de microfone e áudio interno durante o modo de tela.
- Composição OpenGL com cenas de câmera / interval / tela preta.
- Sobreposição de texto e imagem/logotipo no vídeo codificado.
- Filtro Chroma Key GPU que remove verde da câmera e substitui pelo fundo escolhido.
- Controle de mute, ganho de microfone, bitrate e estado de conexão.

### Limitações conhecidas
- Só transmite para um endereço RTMP/SRT por vez. Multistream simultâneo requer relay de servidor ou integrações dedicadas.
- VTuber/VRM, rastreamento facial, widgets de chat, fontes de página web, playlist de vídeo/música e alertas em tempo real ainda não estão implementados.
- A tela de captura deve ser autorizada pelo usuário a cada sessão.
- Áudio de aplicativos pode não ser capturável quando o próprio app de origem bloqueia AudioPlaybackCapture.
- A prévia da tela é ocultada durante o compartilhamento para evitar o efeito de espelho infinito. Depois de autorizar, abra o jogo ou app que deseja transmitir.
- Gravação de teste salva na pasta privada do app.
- O APK do workflow é uma build de depuração, não uma versão assinada para Play Store.

## Compilação
GitHub Actions executa `gradle assembleDebug` e publica o artefato `NovaStreamStudio-APK`.

## Dependência de transmissão
RootEncoder (Apache License 2.0): https://github.com/pedroSG94/RootEncoder
