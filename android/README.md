# E-paper USB para Android

**Autor: Herson Santos · [Licença MIT](../LICENSE)**

**[⬇ Baixar E-paper USB 1.2.0 — APK](https://github.com/hersonrodrigues/e-paper-usb/releases/download/v1.2.0/Epaper-USB-1.2.0-debug.apk)**

Android 8.0 ou superior, com USB OTG. Abra o link no celular, baixe o APK e abra o arquivo para instalar. Se o Android solicitar, permita a instalação pelo navegador ou gerenciador de arquivos utilizado. Não é necessário compilar o projeto. Este é um **APK de debug para testes**; a transmissão e a atualização do painel físico ainda precisam de validação. [Notas da versão e downloads](https://github.com/hersonrodrigues/e-paper-usb/releases/tag/v1.2.0).

Aplicativo Android multilíngue para preparar uma foto e enviá-la por USB OTG a um controlador compatível com o protocolo do ImageToUSB v4.0: Good Display, perfil 800 × 480, quatro cores, modelo de protocolo C4.

**Entrega real:** projeto Android completo e APK de debug compilado no Mac. Testes offline e verificações da interface foram executados. O APK foi instalado e aberto no Pixel 10 conectado. **A transmissão ao painel físico ainda não foi validada.** Instalar o aplicativo no celular não confirma o ACK, a alimentação OTG nem as cores reais do painel.

## Idiomas — versão 1.2.0

O idioma é escolhido automaticamente a partir da configuração do dispositivo. Inglês é o idioma de reserva. No Android 13 ou superior também é possível escolher um idioma em **Configurações → Aplicativos → E-paper USB → Idioma**; por padrão, o aplicativo segue o sistema.

São 25 variantes: inglês, chinês simplificado e tradicional, português do Brasil e de Portugal, espanhol, francês, italiano, alemão, coreano, japonês, hindi, árabe, bengali, russo, indonésio, turco, vietnamita, tailandês, urdu, persa, polonês, neerlandês, ucraniano e tâmil. Textos da interface, mensagens de USB/erro, ajuda, descrições de acessibilidade e rótulos do teste de cores são localizados. Mensagens técnicas no arquivo de diagnóstico usam inglês estável para facilitar a comparação de logs; não incluem conteúdo da foto.

Árabe, persa e urdu usam interface da direita para a esquerda. A prévia e a ordem dos pixels permanecem iguais; apenas os controles e textos acompanham a direção do idioma. Os rótulos da imagem de teste usam composição de texto bidirecional. Traduções podem ser refinadas com revisão de falantes nativos.

Os estados guardam IDs de recursos e são resolvidos no contexto atual da interface. Uma mudança de idioma preserva a prévia já preparada e os bytes correspondentes; o texto de uma imagem de teste já preparada não é alterado silenciosamente. Toque novamente em Teste de cores para gerar seus rótulos no novo idioma.

Para atualizar as traduções, edite `tools/locales/<idioma>.txt`, execute `python3 tools/write_locales.py` e `python3 tools/check_locales.py`. Ao adicionar um idioma, registre-o em `FOLDERS` no gerador e em `resourceConfigurations` de `app/build.gradle`. O gerador produz os XMLs e `app/src/main/res/xml/locales_config.xml`; o verificador confere catálogos, recursos, argumentos de formatação e filtros do Gradle. Indonésio usa `id` no catálogo e em `localeConfig`, e o alias legado `in` nos recursos e filtros. Essa combinação funciona tanto na seleção automática quanto na lista de idiomas do Android.

## Correção 1.0.1

Corrigido o retorno da permissão USB: o `PendingIntent` agora aceita os extras que o Android acrescenta ao responder, como no exemplo oficial da biblioteca. A autorização é confirmada por `UsbManager.hasPermission`; respostas antigas continuam sendo descartadas. O botão **Verificar autorização** recupera uma autorização já concedida sem reenviar pixels. Ao voltar ao aplicativo, pedidos pendentes são conferidos novamente. A interface explica se falta selecionar uma imagem ou conectar o USB.

O teste de regressão reproduziu o travamento da versão 1.0.0 usando `PendingIntent.send`, e a correção passou nos 29 testes offline. A versão 1.0.1 foi instalada e aberta no Pixel 10. Pelo ADB Wi-Fi, o CH340 conectado ao Pixel por OTG foi detectado e o app confirmou USB pronto; ACK e atualização física ainda não foram validados. O registro detalhado está em `reports/CORRECAO-1.0.1.md`.

## Usar no celular

1. Instale o APK pelo link no início deste README e abra **E-paper USB**. Quem compila o projeto pode usar `app/build/outputs/apk/debug/app-debug.apk`.
2. Desconecte o cabo que liga o celular ao Mac e conecte a tela ao celular usando um cabo/adaptador **USB OTG** apropriado. Confira alimentação e a etiqueta física do painel.
3. Toque em **Conectar USB** e aceite a autorização do Android. O app só procura o conversor `1A86:7523`; esse identificador não prova qual painel está conectado. Deixe apenas um conversor compatível conectado.
4. No primeiro teste, escolha **Teste de cores**. Confira a prévia e toque em **Enviar para a tela**. Nada é enviado automaticamente ao conectar.
5. Mantenha o app aberto durante o envio. Ao terminar, aguarde a atualização física e confira o painel. A mensagem de conclusão confirma somente que os bytes foram escritos.
6. Teste **Selecionar foto**, **Foto inteira** (bordas brancas), **Cortar bordas**, **Girar 90°** e **Dithering**. A imagem final tem 800 × 480 pixels; a conversão é feita no celular.
7. Se houver falha, cancelamento, desconexão ou saída do aplicativo durante o envio: **desligue e religue a alimentação da tela**, então use **Já reiniciei a tela** e conecte novamente. Não há comando de abortar confirmado e não há repetição automática.

Não instale `SETUP.EXE` no Mac ou no Android. O app usa o driver CH34x da biblioteca Android diretamente por `UsbManager`. O Mac serve para desenvolver, compilar e instalar o APK, não como intermediário da transferência de pixels.

## Compilar no macOS

Abra esta pasta `android` no Android Studio. Instale pelo SDK Manager: Android SDK Platform 36, Build Tools 35.0.0 e Platform Tools. Deixe o Android Studio configurar o SDK local e use seu JDK incorporado.

Versões fixadas e usadas nesta entrega:

| Componente | Versão |
|---|---|
| Gradle wrapper | 8.12, distribuição com SHA-256 oficial |
| Android Gradle Plugin | 8.10.1 |
| JDK usado | Android Studio JBR 21.0.10 |
| SDK de compilação / alvo | 36 / 36 |
| Android mínimo | 8.0, API 26, com USB host |
| usb-serial-for-android | **3.11.0**, release publicada, JitPack |
| AndroidX Activity / Lifecycle / ExifInterface | 1.13.0 / 2.10.0 / 1.4.2 |

Na raiz deste projeto, onde está `gradlew`:

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk"
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
```

A primeira compilação precisa de internet para obter dependências. Isso se aplica ao ambiente de desenvolvimento; **o aplicativo não declara permissão de internet**. `local.properties` contém o caminho local do SDK e é excluído do ZIP; o Android Studio o gera, ou use `ANDROID_HOME`.

APK gerado: `app/build/outputs/apk/debug/app-debug.apk`.

Para instalar por ADB, com depuração USB já autorizada:

```sh
"$ANDROID_HOME/platform-tools/adb" devices -l
"$ANDROID_HOME/platform-tools/adb" -s SERIAL_DO_CELULAR install -r app/build/outputs/apk/debug/app-debug.apk
```

O APK usa assinatura de debug para desenvolvimento e teste. O ZIP inclui o código e o wrapper, mas não inclui cache do Gradle, SDK, chaves privadas nem artefatos intermediários.

## Protocolo e limites

A evidência original foi copiada de `Downloads/Epaper-USB-para-Mac` para `evidence/`, sem alterar os arquivos. `EpaperProtocol.java` foi incorporado adicionando apenas o package. O teste original também foi preservado e executado.

- Porta serial: **115200, 8N1, RTS=true, DTR=false**. I/O em executor separado da interface.
- Cabeçalho sem vermelho: `AA 55 E1 BB 80 04 C4 00 E3 FF 0D 0A`.
- Com vermelho: `AA 55 E1 BB 80 04 C4 01 E4 FF 0D 0A`.
- `BB 80` representa **48000** no cabeçalho. O payload de quatro cores contém **96000 bytes**.
- Cada byte contém quatro pixels, MSB primeiro. Códigos preservados do dossiê: preto `00`, branco `01`, amarelo `10`, vermelho `11`. A aparência física dos códigos 10/11 ainda precisa ser conferida.
- ACK: janela de **10 bytes**, sincronização `A0 50`, comando `F1`, soma dos primeiros oito bytes no byte 8, terminador `FF`. Demais campos não são inventados nem interpretados como modelo do painel.
- Parser com memória limitada, tolerante a ruído, leituras fragmentadas e concatenadas. Espera máxima de **10 s**, leituras de até 250 ms. ACK inválido, parcial ou F2 não autoriza pixels.
- **23 blocos de 4096 + 1 de 1792**, cada um seguido de `0D 0A`. Esses dois bytes são terminadores fixos, não CRC calculado. Pausa de 100 ms depois de cada bloco completo.
- Timeout de escrita de **5 s por chamada/bloco**; a biblioteca trata escritas USB parciais. Falha encerra a sessão, sem retransmissão automática. Cancelamento pode aguardar até o timeout de uma operação em andamento.
- Total esperado: **96048 bytes nos blocos**, **96060 incluindo cabeçalho**. Aproximadamente 11 segundos de transmissão em condições ideais, mais espera de ACK e processamento do controlador.
- Nenhum descarte de buffer de saída após o envio, comando de refresh ou comando de abortar foi acrescentado.

## Imagem, conexão e privacidade

A seleção usa o seletor de fotos do sistema (com fallback fornecido pelo AndroidX). Fotos até 32 MB são copiadas temporariamente para o cache privado. A decodificação é amostrada para até aproximadamente 3 milhões de pixels e 4096 pixels por lado. Todas as oito orientações EXIF, incluindo espelhos, são normalizadas; transparência é composta sobre branco. O ajuste mantém a proporção, com corte central ou bordas brancas.

A paleta utiliza distância RGB e dithering Floyd–Steinberg opcional. O objeto `PreparedImage` é imutável: o bitmap de prévia e o payload são derivados dos mesmos pixels finais. Enquanto a imagem muda, Enviar fica desabilitado. Falha na conversão invalida a prévia enviável. Rotação da interface preserva o ViewModel e a imagem; reiniciar o processo exige selecionar a foto novamente.

Permissão USB recusada, desconexão e reconexão são tratadas. Respostas de pedidos de permissão antigos são ignoradas. Apenas um executor controla a porta; o app bloqueia envios simultâneos. Um marcador persistente de transmissão incompleta é gravado **antes** do cabeçalho e só removido após sucesso ou confirmação de reinício físico. Sair do app fecha a conexão e cancela um envio ativo; não existe envio em segundo plano.

**Exportar diagnóstico** abre o seletor de documentos com preferência exclusiva por destinos locais. O log contém cabeçalho TX, RX recebido, ACK validado, tamanho/tempo de cada bloco, erros e SHA-256 do payload. Não contém a foto, URI de origem nem dados dos pixels. O log é limitado a 600 linhas em memória; exporte antes de encerrar o processo se quiser preservá-lo. O app não possui acesso à internet e não envia telemetria. A cópia temporária da foto é removida ao liberar o modelo ou na próxima inicialização.

## Validação

Veja [`../docs/validation.md`](../docs/validation.md) no repositório. Relatórios completos de execução e capturas ficam em `reports/` na entrega local, fora do Git. O Gradle também gera relatórios em `app/build/reports/`.

- **Compilação:** APK de debug gerado; Android Lint sem erros. Os seis avisos restantes tratam de versões mais recentes disponíveis, `localeConfig` usado apenas no Android 13+ e sugestão de plural para a mensagem de tamanho fixo de 96000 bytes. As versões testadas foram mantidas fixas.
- **Offline:** vetores originais, checksum, paleta, tamanho e blocos; ACK fragmentado/concatenado, ruído, F2 e checksum inválido; timeout; desconexão em leitura/escrita; cancelamento; concorrência; igualdade de prévia/payload; transparência, oito orientações EXIF, limites de amostragem, proporção/corte; recusa de permissão, reconexão e sessão incompleta; preservação em recriação da Activity.
- **Emulador:** interface e preparação de imagem. O emulador não comprova USB OTG ou atualização do e-paper.
- **Pixel 10:** instalação e abertura confirmadas por ADB. Nenhum ACK real foi recebido nesta etapa e nenhuma atualização do painel é alegada.

### Roteiro do primeiro teste físico

1. Confira o modelo na etiqueta: resolução 800 × 480 e quatro cores. Registre a etiqueta separadamente; VID/PID só identifica o CH340.
2. Conecte ao celular via OTG e autorize o USB. Exporte o diagnóstico se a porta não abrir.
3. Selecione o padrão de teste e envie uma única vez. Aguarde a atualização visual.
4. Confira as quatro faixas, rótulos de cores, `TOPO / 1` e `BASE / 2`. Verifique orientação, alimentação e tempo de atualização.
5. Exporte o log: conferir TX do cabeçalho, ACK **real**, 24 blocos e o fim do envio. A aprovação física exige comparar a tela com a prévia.
6. Se der timeout ou desconectar, pare a sessão e reinicie a alimentação da tela antes de repetir. Não tente F2, outro modelo ou bytes de ACK inventados.

## Organização

- `app/src/main/java/com/santos/epaperusb/protocol/`: núcleo original, parser ACK e transporte testável.
- `image/`: EXIF, amostragem, ajuste, quantização, snapshot imutável.
- `usb/UsbController.java`: permissão, CH34x, ciclo da porta e estado persistente.
- `MainActivity.java`, `AppModel.java`: interface e tarefas fora da UI.
- `app/src/test/`: JUnit + Robolectric; transportes simulados aparecem somente nos testes.
- `evidence/`: dossiê recebido e licença MIT da biblioteca USB.

## Fontes oficiais

- [usb-serial-for-android: README e uso](https://github.com/mik3y/usb-serial-for-android)
- [Release 3.11.0](https://github.com/mik3y/usb-serial-for-android/releases/tag/3.11.0)
- [Android USB host](https://developer.android.com/develop/connectivity/usb/host)
- [Seletor de fotos Android](https://developer.android.com/training/data-storage/shared/photo-picker)
- [EXIF AndroidX](https://developer.android.com/jetpack/androidx/releases/exifinterface)
- [AGP 8.10: requisitos](https://developer.android.com/build/releases/agp-8-10-0-release-notes)
- [Compilar na linha de comando](https://developer.android.com/build/building-cmdline)
- [Good Display GDP075FU1](https://www.good-display.com/product/640.html)
- [Manual EN-GDP075FU1.pdf](https://v4.cecdn.yun300.cn/100001_1909185148/EN-GDP075FU1.pdf)

A página do fabricante é compatível com o dossiê, mas não substitui a identificação física da tela. O dossiê descreve análise estática do executável; esta entrega acrescenta compilação e testes reais do aplicativo Android, sem transformar a análise estática em prova de compatibilidade física.
