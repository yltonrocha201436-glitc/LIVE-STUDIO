# NovaStream Studio

Aplicativo Android nativo em Kotlin e Jetpack Compose. O projeto usa módulos separados para interface e motor de mídia.

## Versão atual: 0.4.0

### Implementado no código
- Estúdio dividido em cinco áreas: Ao Vivo, Cenas, Fontes, Áudio e Ajustes.
- Prévia de câmera via OpenGL e câmera frontal/traseira.
- Motor RootEncoder para transmissão RTMP/RTMPS e SRT.
- Gravação MP4 local usando o mesmo pipeline de mídia.
- Composição por GPU com cenas de câmera e tela preta/espera.
- Sobreposição de texto e imagem/logotipo no fluxo de vídeo.
- Filtro Chroma Key baseado em OpenGL.
- Controle de volume e mute do microfone.
- Cancelamento de eco e supressão de ruído solicitados ao Android.
- Ajuste dinâmico de bitrate durante transmissão.
- Pipeline GitHub Actions para gerar APK de teste.

### Limitações da versão 0.4.0
- Um destino RTMP/SRT por vez. Multistream precisa de um relay de servidor ou integração específica.
- Captura de tela ainda não está integrada.
- VTuber/VRM e rastreamento facial não estão integrados.
- Widgets de chat, páginas web, playlists e vídeo como fonte ainda não estão implementados.
- A transmissão não continua em segundo plano nesta versão.
- A gravação vai para a pasta privada de filmes do aplicativo.
- O APK gerado pelo workflow é de depuração e não um pacote assinado para Play Store.

## Compilação
O workflow `.github/workflows/build-apk.yml` executa `gradle assembleDebug` no GitHub Actions e publica o artefato `NovaStreamStudio-APK`.

## Motor de mídia
A dependência RootEncoder é distribuída sob Apache License 2.0:
https://github.com/pedroSG94/RootEncoder
