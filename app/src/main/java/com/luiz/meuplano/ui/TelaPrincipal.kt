package com.luiz.meuplano.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luiz.meuplano.R
import com.luiz.meuplano.data.acharItemDaDieta
import com.luiz.meuplano.data.acharSerie
import com.luiz.meuplano.data.edicao
import com.luiz.meuplano.trocas.reconhecerItem
import com.luiz.meuplano.video.EdicaoDoClipe
import com.luiz.meuplano.video.Marca

/** As quatro seções do app, na ordem da barra de baixo. */
private enum class Secao(val titulo: String, @param:DrawableRes val icone: Int) {
    TREINO("Treino", R.drawable.ic_treino),
    DIETA("Dieta", R.drawable.ic_dieta),
    CAMERA("Câmera", R.drawable.ic_camera),
    FEEDBACK("Feedback", R.drawable.ic_feedback),
    INFO("Info", R.drawable.ic_info),
}

@Composable
fun TelaPrincipal(viewModel: PlanoViewModel) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }

    var secao by rememberSaveable { mutableIntStateOf(0) }
    // Ids da série que está em cada tela, ou nulo quando a tela está fechada.
    var serieNaCamera by rememberSaveable { mutableStateOf<Long?>(null) }
    var serieNoCorte by rememberSaveable { mutableStateOf<Long?>(null) }
    var serieNoRegistro by rememberSaveable { mutableStateOf<Long?>(null) }
    // Comparar entre semanas: a série de onde foi aberto.
    var serieNoComparar by rememberSaveable { mutableStateOf<Long?>(null) }
    // Verdadeiro quando o corte abriu logo depois de uma gravação: ao fechar, pede carga e RPE.
    var registrarDepoisDoCorte by rememberSaveable { mutableStateOf(false) }
    // O vídeo que a câmera acabou de entregar (nome do arquivo) e o corte inicial dele.
    var recemGravado by rememberSaveable { mutableStateOf<String?>(null) }
    var corteDoRecemGravado by rememberSaveable { mutableStateOf<Long?>(null) }
    // O Montador de Trocas: fechado, solto, ou aberto por um item da dieta (aí guarda o id dele).
    var montadorAberto by rememberSaveable { mutableStateOf(false) }
    // Câmera fora das séries: o nome de uma Marca (aba Câmera) ou FISICO (vídeo do feedback).
    var cameraLivre by rememberSaveable { mutableStateOf<String?>(null) }
    var itemNoMontador by rememberSaveable { mutableStateOf<Long?>(null) }

    // Seletor de arquivos do próprio Android: não precisa de permissão de armazenamento.
    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importar(uri)
    }
    val escolherArquivo = { seletor.launch(arrayOf("text/html", "application/xhtml+xml")) }

    // Vídeo da série feito com a câmera do celular: escolhido na galeria, depois abre o corte.
    var serieDaGaleria by rememberSaveable { mutableStateOf<Long?>(null) }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val serieId = serieDaGaleria
        serieDaGaleria = null
        if (uri != null && serieId != null) {
            viewModel.importarVideoDaSerie(serieId, uri) { arquivo ->
                recemGravado = arquivo.name
                corteDoRecemGravado = null
                serieNoCorte = serieId
                registrarDepoisDoCorte = true
            }
        }
    }

    LaunchedEffect(mensagem) {
        val texto = mensagem ?: return@LaunchedEffect
        avisos.showSnackbar(texto)
        // Só limpa depois de exibir: limpar antes mudaria a chave e cancelaria o aviso.
        viewModel.mensagemMostrada()
    }

    // Guarda o estado de cada aba (rolagem, treino escolhido, cartões abertos) enquanto ela está fora da tela.
    val estadoDasAbas = rememberSaveableStateHolder()

    val plano = (estado as? EstadoPlano.Carregado)?.plano
    val gravando = plano?.acharSerie(serieNaCamera)
    val cortando = plano?.acharSerie(serieNoCorte)

    when {
        // ---------- vídeo do físico, para o feedback ----------
        cameraLivre == CAMERA_DO_FISICO && plano != null -> {
            val planoId = plano.plano.id
            TelaCamera(
                titulo = "Vídeo do físico",
                detalhe = "Frente · Lado · Costas · Perna",
                rpe = null,
                criarArquivo = { viewModel.novoArquivoDoFisico(planoId) },
                onGravado = { arquivo, _ ->
                    viewModel.fisicoGravado(planoId, arquivo)
                    cameraLivre = null
                },
                onCarga = null,
                onFechar = { cameraLivre = null },
                dica = "Grave virando devagar: frente, lado, costas e perna",
            )
        }

        // ---------- câmera livre da aba Câmera (foto ou vídeo) ----------
        cameraLivre != null && plano != null -> {
            val capturas by viewModel.capturas.collectAsStateWithLifecycle()
            val marca = Marca.entries.firstOrNull { it.name == cameraLivre } ?: Marca.LIVRE
            TelaCamera(
                titulo = marca.rotulo,
                detalhe = diaEmDestaque(java.time.LocalDate.now()),
                rpe = null,
                criarArquivo = { viewModel.novoArquivoDeCaptura(marca) },
                onGravado = { _, _ ->
                    viewModel.capturaGravada()
                    cameraLivre = null
                },
                onCarga = null,
                onFechar = { cameraLivre = null },
                onFotografado = { crua ->
                    viewModel.fotoTirada(crua, marca)
                    cameraLivre = null
                },
                // Na aba Câmera: Salvar ou Refazer antes de ir para a galeria.
                revisar = true,
                ultimaCaptura = capturas.firstOrNull()?.arquivo,
                onAbrirUltima = { cameraLivre = null },
            )
        }

        // ---------- câmera: ocupa a tela inteira ----------
        gravando != null -> {
            val serieId = gravando.item.serie.id
            val (detalhe, rpe) = gravando.item.resumoParaCamera()
            TelaCamera(
                titulo = gravando.exercicio.nome,
                detalhe = detalhe,
                rpe = rpe,
                criarArquivo = { viewModel.novoArquivoDeVideo(serieId) },
                onGravado = { arquivo, corteInicial ->
                    viewModel.videoGravado(serieId, arquivo, corteInicial)
                    // A edição abre direto com este arquivo, sem esperar o banco atualizar.
                    recemGravado = arquivo.name
                    corteDoRecemGravado = corteInicial
                    serieNaCamera = null
                    // Com o vídeo salvo: primeiro o corte, depois a carga e o RPE.
                    serieNoCorte = serieId
                    registrarDepoisDoCorte = true
                },
                onCarga = { serieNoRegistro = serieId },
                onFechar = { serieNaCamera = null },
                gravarDuranteAContagem = true,
            )
        }

        // ---------- corte do vídeo: também ocupa a tela inteira ----------
        cortando != null -> {
            val serieId = cortando.item.serie.id
            val registro = cortando.item.registro
            // Logo depois de gravar, o banco ainda pode estar com o vídeo anterior: vale o recém-gravado.
            val doBanco = registro?.videoArquivo
            val video = if (registrarDepoisDoCorte && recemGravado != null) recemGravado else doBanco
            val edicaoInicial = if (video != null && video == doBanco) {
                registro?.edicao() ?: EdicaoDoClipe()
            } else {
                EdicaoDoClipe(inicioMs = corteDoRecemGravado)
            }
            val fecharCorte: () -> Unit = {
                recemGravado = null
                corteDoRecemGravado = null
                serieNoCorte = null
                if (registrarDepoisDoCorte) {
                    registrarDepoisDoCorte = false
                    serieNoRegistro = serieId
                }
            }
            if (video == null) {
                // O vídeo acabou de ser gravado e ainda está sendo ligado à série.
                BackHandler { fecharCorte() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Cores.Fundo),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Cores.Destaque)
                }
            } else {
                // Uma tela nova para cada vídeo: sem isso, o player podia ficar com o vídeo anterior.
                key(serieId, video) {
                    TelaCorte(
                        titulo = cortando.exercicio.nome,
                        arquivo = viewModel.arquivoDeVideo(video),
                        edicaoSalva = edicaoInicial,
                        onSalvar = { edicao ->
                            viewModel.salvarEdicao(serieId, edicao)
                            fecharCorte()
                        },
                        onRegravar = {
                            recemGravado = null
                            corteDoRecemGravado = null
                            serieNoCorte = null
                            registrarDepoisDoCorte = false
                            serieNaCamera = serieId
                        },
                        onFechar = fecharCorte,
                    )
                }
            }
        }

        // ---------- montador de trocas: também ocupa a tela inteira ----------
        montadorAberto && plano != null -> {
            val doDia by viewModel.estadoDoDia.collectAsStateWithLifecycle()
            val itemId = itemNoMontador
            // O item da dieta que abriu o montador, se foi um e o app reconhece o alimento.
            val daDieta = remember(plano, itemId, doDia.trocas) {
                val item = plano.acharItemDaDieta(itemId)
                val reconhecido = item?.let { reconhecerItem(it.texto) }
                if (item != null && reconhecido != null) {
                    ItemParaTrocar(item.id, item.texto, reconhecido, doDia.trocas[item.id])
                } else {
                    null
                }
            }
            val fecharMontador = {
                montadorAberto = false
                itemNoMontador = null
            }
            TelaTrocas(
                daDieta = daDieta,
                onUsar = { origem, quantidade, destino1, destino2, proporcao ->
                    if (daDieta != null) {
                        viewModel.usarTroca(daDieta.itemId, origem, quantidade, destino1, destino2, proporcao)
                    }
                    fecharMontador()
                },
                onDesfazer = {
                    if (daDieta != null) viewModel.desfazerTroca(daDieta.itemId)
                    fecharMontador()
                },
                onFechar = fecharMontador,
            )
        }

        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Cores.Fundo),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        // Com o teclado aberto, o conteúdo encolhe em vez de ficar escondido atrás dele.
                        .imePadding(),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        when {
                            plano != null -> {
                                val atual = Secao.entries[secao.coerceIn(0, Secao.entries.size - 1)]
                                estadoDasAbas.SaveableStateProvider(atual.name) {
                                    when (atual) {
                                        Secao.TREINO -> AbaTreino(
                                            plano = plano,
                                            onRegistrar = { serieNoRegistro = it },
                                            onGravar = { serieNaCamera = it },
                                            onVerVideo = {
                                                registrarDepoisDoCorte = false
                                                serieNoCorte = it
                                            },
                                            onImportar = escolherArquivo,
                                        )
                                        Secao.DIETA -> AbaDieta(
                                            plano = plano,
                                            viewModel = viewModel,
                                            onAbrirMontador = { itemId ->
                                                itemNoMontador = itemId
                                                montadorAberto = true
                                            },
                                        )
                                        Secao.CAMERA -> AbaCamera(
                                            viewModel = viewModel,
                                            onAbrirCamera = { cameraLivre = it.name },
                                        )
                                        Secao.FEEDBACK -> AbaFeedback(
                                            plano = plano,
                                            viewModel = viewModel,
                                            onGravarFisico = { cameraLivre = CAMERA_DO_FISICO },
                                        )
                                        Secao.INFO -> AbaInfo(plano)
                                    }
                                }
                            }
                            estado == EstadoPlano.Vazio -> SemPlano(onEscolher = escolherArquivo)
                            else -> CircularProgressIndicator(
                                color = Cores.Destaque,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                    if (plano != null) {
                        BarraDeSecoes(selecionada = secao, onSelecionar = { secao = it })
                    }
                }
                SnackbarHost(
                    hostState = avisos,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 72.dp),
                )
            }
        }
    }

    // ---------- registro de carga e RPE: por cima da tela principal ou da câmera ----------
    val registrando = plano?.acharSerie(serieNoRegistro)
    if (registrando != null && cortando == null) {
        val serieId = registrando.item.serie.id
        // A letra do treino da série, para o cabeçalho do registro.
        val letra = plano?.treinos?.firstOrNull { t -> t.exercicios.any { it.exercicio.id == registrando.exercicio.id } }
            ?.treino?.letra
        DialogoRegistro(
            localizada = registrando,
            treino = letra,
            ultimaVez = { viewModel.ultimaVez(registrando.exercicio) },
            outrasSemanas = {
                val todos = viewModel.videosDoExercicio(registrando.exercicio)
                todos.size >= 2 && todos.any { it.planoId != plano?.plano?.id }
            },
            onComparar = {
                serieNoRegistro = null
                serieNoComparar = serieId
            },
            onEscolherDaGaleria = {
                serieNoRegistro = null
                serieDaGaleria = serieId
                galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            // Aberto pelo botão "Carga" da câmera: só os campos, sem os botões de vídeo.
            naCamera = gravando != null,
            onSalvar = { carga, rpe, anotacao ->
                viewModel.salvarRegistro(serieId, carga, rpe, anotacao)
                serieNoRegistro = null
            },
            onGravar = {
                serieNoRegistro = null
                serieNaCamera = serieId
            },
            onVerVideo = {
                serieNoRegistro = null
                registrarDepoisDoCorte = false
                serieNoCorte = serieId
            },
            onApagar = {
                viewModel.limparRegistro(serieId)
                serieNoRegistro = null
            },
            onFechar = { serieNoRegistro = null },
        )
    }

    // ---------- comparar a execução do exercício entre semanas ----------
    val noComparar = plano?.acharSerie(serieNoComparar)
    if (plano != null && noComparar != null) {
        CompararSemanas(
            exercicio = noComparar.exercicio,
            serieAtual = noComparar.item.serie.id,
            planoAtual = plano.plano.id,
            viewModel = viewModel,
            onFechar = { serieNoComparar = null },
        )
    }
}

/** Valor de cameraLivre para o vídeo do físico. */
private const val CAMERA_DO_FISICO = "FISICO"

/** A barra de baixo do design: quatro seções, a escolhida em laranja. */
@Composable
private fun BarraDeSecoes(selecionada: Int, onSelecionar: (Int) -> Unit) {
    Column(modifier = Modifier.background(Cores.Fundo)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Cores.BordaApagada),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .height(60.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Secao.entries.forEachIndexed { i, item ->
                val escolhida = i == selecionada
                val cor = if (escolhida) Cores.Destaque else Cores.TextoSuave
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .clickable { onSelecionar(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Icon(painterResource(item.icone), contentDescription = null, tint = cor, modifier = Modifier.size(24.dp))
                    Text(
                        text = item.titulo,
                        style = Tipos.CorpoPequeno.copy(
                            fontSize = 12.sp,
                            fontWeight = if (escolhida) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                        color = cor,
                    )
                }
            }
        }
    }
}

@Composable
private fun SemPlano(onEscolher: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "SEM PLANO", style = Tipos.TituloDeTela, color = Cores.Texto)
        Text(
            text = "Toque no arquivo .html que o treinador enviou e escolha " +
                "\"Abrir com\" → Meu Plano. Ou procure o arquivo por aqui.",
            style = Tipos.Corpo,
            color = Cores.TextoSuave,
            textAlign = TextAlign.Center,
        )
        BotaoPrincipal("Escolher arquivo", onEscolher, icone = R.drawable.ic_importar)
    }
}
