# Meu Plano

App Android que importa o HTML semanal do treinador (treino e dieta).

## O que já tem

- Parser do HTML (`parser/`), com testes contra um plano de exemplo
- Banco local Room (`data/`)
- Importação: "Abrir com" / "Compartilhar" de um `.html`, ou o botão dentro do app
- Telas de consulta: Treino (A–E e Cardio), Dieta e Info
- Registro de carga e RPE por série (toque na série), numa folha que sobe de baixo:
  carga com −/+ de 2,5 kg e atalhos de ±1 e ±5 (ou digitada), "última vez" da semana
  anterior, RPE em botões de 6 a 10 com o prescrito marcado e a anotação
- Câmera dentro do app (`ui/TelaCamera.kt`): 1080p a 16 Mbps, contagem antes de começar
  ("Iniciar em": Off, 3s, 5s, 10s), lente frontal/traseira, grade de terços, e não
  pausa a música que está tocando. O vídeo não tem limite de duração. O botão "Carga"
  abre o registro de carga e RPE sem sair da câmera. O botão do raio abre os ajustes:
  iluminação, visual (Sem, Leve ou Forte: cor contida, preto firme, sombras frias e luzes
  quentes, feito como tabela de cores em `video/VisualDaCamera.kt`) e som; iluminação e
  visual ficam guardados
- Câmera no layout simplificado do design: topo com fechar, cronômetro, contagem (toque
  troca Off/3s/5s/10s) e configurações; a série numa linha só; embaixo VÍDEO/FOTO, o
  disparo e virar a câmera. Configurações numa folha: contagem, flash (lanterna no vídeo),
  visual, iluminação, faixa da série, grade e som
- Aba Câmera (`ui/AbaCamera.kt`, `video/Capturas.kt`): fotos e vídeos fora das séries
  (aquecimento, backoff, livre), com o mesmo visual. Galeria separada por dia, com filtro,
  botão Gravar e Comparar (marca A e B; `ui/TelaComparar.kt`: lado a lado, empilhado ou
  fantasma, início de cada vídeo ajustável, ¼×/½×/1×, quadro a quadro e repetir); o player
  tem câmera lenta (0,5x), baixar, enviar e apagar. Segurar uma miniatura entra na seleção
  para apagar várias de uma vez (toque marca/desmarca, "Todos", confirmação em dois toques).
- Comparar entre semanas (`ui/CompararSemanas.kt`, `PlanoDao.videosDoExercicio`): no registro da
  série, "Comparar com outras semanas" lista os vídeos do mesmo exercício (pela chave) de todos
  os planos importados, já com A = esta semana e B = a última outra semana; abre o mesmo
  Comparar, começando cada vídeo no corte e com a semana e a carga na etiqueta. Segurar uma linha marca
  para apagar só aquele vídeo (mantém a carga e o RPE do registro), com confirmação em dois
  toques — útil para limpar vídeos de teste antigos que ficaram presos a uma série
  (`PlanoRepository.apagarVideoDaSerie`). Na foto o visual é aplicado depois de
  tirada (`video/ProcessadorDeFoto.kt`), com a mesma tabela de cores do vídeo
- Feedback: as três fotos viraram um vídeo único do físico (frente, lado, costas e
  perna), gravado pela própria câmera do app e enviado junto com os vídeos dos treinos
- Edição do clipe (`ui/TelaCorte.kt`, regras em `video/Edicao.kt`): corte com alças na
  própria faixa de miniaturas (arrastar fora das alças só mostra aquele ponto) e ajuste
  quadro a quadro, trecho em câmera lenta com velocidade ajustável (¼×, ½× ou ¾×),
  congelar o último quadro (1 s), zoom (dois dedos ou o controle; arrastar a imagem
  enquadra), grade de enquadramento por cima do vídeo (só visual, não é salva) e espelhar
  na horizontal. A prévia mostra o clipe como ele vai sair (zoom, espelho, lento e o final
  parado), sem som. As ferramentas ficam em abas (Corte, Lento, Zoom, Mais), o Salvar fica
  sempre à vista e fechar com mudanças pede confirmação. O arquivo original não é alterado:
  só a edição é guardada e aplicada na montagem (que espelha antes de recortar o zoom,
  igual à prévia)
- Limpeza ao abrir (`PlanoViewModel.limparSobras`, `PlanoRepository.apagarVideosSemSerie`):
  apaga temporários e vídeos sem série que operações interrompidas deixaram para trás
- Backup do Android (`res/xml/regras_de_backup*.xml`): só o banco e as preferências; os
  vídeos e as fotos ficam só no aparelho
- Foco e luz no toque (`TelaCamera.kt`, `MiraDeFoco`): tocar no visor foca e mede a luz ali,
  com o quadrado amarelo; o sol ao lado ajusta a iluminação arrastando (a mesma das
  configurações); segurar o dedo, ou o cadeado embaixo do quadrado, trava foco, exposição e
  balanço de branco ("TRAVA AE/AF", via Camera2 interop). Tocar de novo destrava
- Vídeo da câmera do celular (`PlanoViewModel.importarVideoDaSerie` / `importarCaptura`): no
  registro da série, o botão da galeria traz um vídeo já feito e abre o corte; na aba Câmera, o
  botão redondo acima do Gravar traz foto ou vídeo com a marca do filtro. Vídeos em HDR são
  convertidos para o normal na montagem
- Corte dos vídeos da aba Câmera (`TelaCorte` com `soCorte`, `PlanoViewModel.cortarCaptura`):
  "Cortar" no player; o arquivo passa a ter só o trecho escolhido
- Exercício com a série escrita fora da lista no HTML (ex.: "1x1- peso corporal (Gravar)" num
  parágrafo): vira série (`SerieParser.seriesDaObservacao`); planos já importados são
  consertados ao abrir o app (`PlanoRepository.consertarExerciciosSemSerie`)
- Montagem (`video/MontadorDeVideo.kt`): além da edição de cada clipe, fade entre os
  clipes, legenda entrando com animação e som (mudo, original ou música do celular).
  Se o aparelho não aguentar os efeitos, monta de novo numa versão simplificada.
  Ideias de edição inspiradas no Concat (github.com/jub0t/concat), sem código copiado
- Dieta do dia (`ui/AbaDieta.kt`): água, refeições feitas por opção e um único check,
  "furei a dieta", por data. Sem o check, o dia conta como dieta seguida na aderência
  do feedback
- Montador de Trocas (`trocas/`, `ui/TelaTrocas.kt`): o material do treinador dentro do
  app. Troca um alimento por um ou dois outros mantendo as calorias, com o mesmo banco de
  alimentos (87 itens, base TACO) e as mesmas regras de arredondamento. Na aba Dieta, cada
  alimento que está no banco ganha um botão de troca que abre o montador já preenchido;
  a troca escolhida vale só para aquele dia e não entra no feedback
- Feedback semanal (`feedback/`, `ui/AbaFeedback.kt`): texto no modelo do treinador
  preenchido com os registros (em "Progressões de Carga" só entra PR batido, que o app
  detecta pela tabela de PRs), fotos da semana e um vídeo por treino com os clipes
  cortados e a legenda de carga e RPE no canto inferior esquerdo, sem fundo
  (`video/MontadorDeVideo.kt`, Media3 Transformer). Tocar no vídeo montado abre o player
  (`ui/TelaDoVideo.kt`) com Enviar (só ele) e Baixar; em "Clipes", cada vídeo pode ser
  assistido e baixado para a galeria (Filmes/MeuPlano), só o trecho cortado, com som e
  sem legenda (`video/Galeria.kt`)

Mudanças nas tabelas do banco são feitas por migração (`data/AppDatabase.kt`), sem
apagar os dados.

## Abrir

Android Studio → **Open** → escolha esta pasta e aguarde o Gradle Sync.
O SDK usado é o Android 16 (API 36); o Studio oferece para baixar se faltar.

## Testes

    ./gradlew :app:testDebugUnitTest

`PlanoParserTest` confere o parser contra um plano de exemplo
(`app/src/test/resources/plano_exemplo.html`): inventado, sem dados de ninguém, mas no mesmo
formato do HTML que o treinador envia. `FeedbackTest` confere o texto do feedback com uma
semana de exemplo.

## Design

O visual segue o design do app: tema escuro, laranja de destaque,
Barlow e Barlow Condensed (embutidas em `res/font`, licença OFL), barra de seções
embaixo. Cores, fontes e estilos de texto ficam em `ui/Tema.kt`, as peças que se
repetem em `ui/Componentes.kt`, e o que transforma dado em texto de tela em
`ui/Apresentacao.kt`.

## Rodar rápido (variante release)

A variante **debug** do Android roda devagar, com travadinhas na rolagem e na troca de
tela. Para usar o app de verdade, instale a **release**: no Android Studio, abra
**Build → Select Build Variant…** e troque `debug` por `release` no módulo `app`.
Ela é assinada com a chave de testes, então instala pelo ▶ do mesmo jeito.

## O que falta

- Som no vídeo final (hoje ele sai mudo)
- Ajustes da aba Dieta (a definir)

## Licença

MIT, veja o arquivo `LICENSE`. As fontes em `app/src/main/res/font` (Barlow, Barlow Condensed
e JetBrains Mono) são dos respectivos autores, sob a SIL Open Font License 1.1.
