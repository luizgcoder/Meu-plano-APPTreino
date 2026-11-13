package com.luiz.meuplano.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luiz.meuplano.R
import com.luiz.meuplano.data.PlanoCompleto
import com.luiz.meuplano.data.edicao
import com.luiz.meuplano.feedback.ClipeDoTreino
import com.luiz.meuplano.feedback.TreinoComClipes
import com.luiz.meuplano.feedback.legendaDoClipe
import com.luiz.meuplano.video.OpcoesDaMontagem
import com.luiz.meuplano.video.SomDoVideo
import java.io.File

/**
 * O feedback da semana no layout do design: os números da semana, o texto no modelo
 * do treinador (editável), o vídeo do físico e um vídeo por treino.
 */
@Composable
fun AbaFeedback(plano: PlanoCompleto, viewModel: PlanoViewModel, onGravarFisico: () -> Unit) {
    val estado by viewModel.feedback.collectAsStateWithLifecycle()
    val textoEditado by viewModel.textoEditado.collectAsStateWithLifecycle()
    val fisico by viewModel.fisico.collectAsStateWithLifecycle()
    val videosProntos by viewModel.videosProntos.collectAsStateWithLifecycle()
    val montagem by viewModel.montagem.collectAsStateWithLifecycle()
    val baixando by viewModel.baixando.collectAsStateWithLifecycle()
    val baixados by viewModel.baixados.collectAsStateWithLifecycle()
    val opcoes by viewModel.opcoesDaMontagem.collectAsStateWithLifecycle()
    // Música de fundo: o seletor de arquivos do Android, sem pedir permissão.
    val seletorDeMusica = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.escolherMusica(uri)
    }
    val contexto = LocalContext.current
    // O vídeo aberto em tela cheia (o montado ou um clipe), ou nulo.
    var aberto by remember { mutableStateOf<VideoAberto?>(null) }

    val feedback = estado
    if (feedback == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cores.Destaque)
        }
        return
    }

    val planoId = feedback.planoId
    val treinoIds = feedback.treinos.map { it.treino.id }
    val texto = textoEditado ?: feedback.textoAutomatico
    val montando = montagem as? EstadoDaMontagem.Montando

    // Fotos e vídeos ficam em pastas; relê quando o plano ou a lista de treinos com vídeo muda.
    LaunchedEffect(planoId, treinoIds) {
        viewModel.atualizarArquivosDoFeedback(planoId, treinoIds)
    }
    LaunchedEffect(planoId) {
        viewModel.carregarTextoEditado(planoId)
    }

    val videosParaEnviar = feedback.treinos.mapNotNull { videosProntos[it.treino.id] }

    Column(modifier = Modifier.fillMaxSize()) {
        CabecalhoDeTela(
            sobretitulo = (plano.plano.semana?.let { "Semana $it · " } ?: "") +
                periodoCurto(feedback.dados.inicio, feedback.dados.fim),
            titulo = "Feedback",
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ----- os números da semana -----
            item(key = "numeros") {
                val cardios = feedback.dados.checkins.count { it.cardioFeito }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumeroDaSemana(
                        "Treinos",
                        remember(plano) { plano.treinos.count { treino -> treino.exercicios.any { it.feito() } } }.toString(),
                        "/${plano.treinos.size}",
                        Modifier.weight(1f),
                    )
                    NumeroDaSemana(
                        "Cardio",
                        cardios.toString(),
                        feedback.dados.cardioMetaDias?.let { "/$it" }.orEmpty(),
                        Modifier.weight(1f),
                    )
                    NumeroDaSemana(
                        "Vídeos",
                        feedback.treinos.sumOf { it.clipes.size }.toString(),
                        "",
                        Modifier.weight(1f),
                    )
                }
            }

            // ----- lembrete de PR -----
            if (feedback.dados.prsBatidos.isNotEmpty()) {
                item(key = "prs") {
                    Cartao(destacado = true, espaco = 6.dp) {
                        Rotulo("PR batido nesta semana", cor = Cores.Destaque)
                        for (pr in feedback.dados.prsBatidos) {
                            Text(
                                text = "${pr.pr}: ${pr.cargaKg.semZeros()} kg",
                                style = Tipos.TituloDeSecao,
                                color = Cores.Texto,
                            )
                            Text(
                                text = "No ${pr.exercicio}. O recorde era ${pr.anteriorKg.semZeros()} kg.",
                                style = Tipos.CorpoPequeno,
                                color = Cores.TextoSuave,
                            )
                        }
                        if (textoEditado != null) {
                            // O texto editado não se atualiza sozinho: avisa para não esquecer do PR.
                            Text(
                                text = "Você editou o texto: confira se o PR está nas progressões.",
                                style = Tipos.CorpoPequeno,
                                color = Cores.NovoTexto,
                            )
                        }
                    }
                }
            }

            // ----- texto -----
            item(key = "texto") {
                Cartao {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TEXTO", style = Tipos.TituloDeSecao, color = Cores.Texto, modifier = Modifier.weight(1f))
                        if (textoEditado == null) {
                            Text(
                                text = "Gerado automaticamente",
                                style = Tipos.CorpoPequeno.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                                color = Cores.TextoSuave,
                            )
                        } else {
                            Text(
                                text = "Editado · desfazer",
                                style = Tipos.CorpoPequeno.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                                color = Cores.Destaque,
                                modifier = Modifier.clickable { viewModel.voltarAoTextoAutomatico(planoId) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { viewModel.editarTexto(planoId, it) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = Tipos.CorpoPequeno.copy(fontFamily = Mono, fontSize = 12.5.sp, lineHeight = 19.sp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Cores.Fundo,
                            unfocusedContainerColor = Cores.Fundo,
                            focusedTextColor = Cores.Texto,
                            unfocusedTextColor = Cores.Texto,
                            focusedBorderColor = Cores.BordaForte,
                            unfocusedBorderColor = Cores.Trilho,
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotaoPrincipal(
                            texto = "Enviar no WhatsApp",
                            onClick = { enviarTexto(contexto, texto) },
                            modifier = Modifier.weight(1f),
                            icone = R.drawable.ic_enviar,
                        )
                        BotaoQuadrado(
                            icone = R.drawable.ic_copiar,
                            descricao = "Copiar texto",
                            onClick = { copiarTexto(contexto, texto) },
                            fundo = Cores.Fundo,
                            tamanho = 48.dp,
                        )
                    }
                }
            }

            // ----- físico: um vídeo só, no lugar das três fotos -----
            item(key = "fisico") {
                Cartao {
                    Text("FÍSICO", style = Tipos.TituloDeSecao, color = Cores.Texto)
                    val video = fisico
                    if (video == null) {
                        Text(
                            text = "Um vídeo só, virando devagar: frente, lado, costas e perna.",
                            style = Tipos.Corpo,
                            color = Cores.TextoSuave,
                        )
                        BotaoPrincipal(
                            texto = "Gravar vídeo do físico",
                            onClick = onGravarFisico,
                            modifier = Modifier.fillMaxWidth(),
                            icone = R.drawable.ic_gravar,
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    aberto = VideoAberto(
                                        chave = "fisico-${video.lastModified()}",
                                        titulo = "Vídeo do físico",
                                        subtitulo = "Frente · Lado · Costas · Perna · ${megabytes(video.length())}",
                                        arquivo = video,
                                        inicioMs = null,
                                        fimMs = null,
                                        nomeNaGaleria = "Fisico",
                                        podeEnviar = true,
                                    )
                                },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Cores.NovoFundo),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_video),
                                    contentDescription = "Ver o vídeo do físico",
                                    tint = Cores.Destaque,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Gravado", style = Tipos.CorpoForte, color = Cores.Texto)
                                Text(
                                    text = "${megabytes(video.length())} · toque para ver",
                                    style = Tipos.CorpoPequeno,
                                    color = Cores.FeitoTexto,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotaoContorno("Regravar", onGravarFisico, Modifier.weight(1f), fundo = Cores.Superficie)
                            BotaoContorno(
                                texto = "Remover",
                                onClick = { viewModel.removerFisico(planoId) },
                                modifier = Modifier.weight(1f),
                                fundo = Cores.Superficie,
                            )
                        }
                    }
                }
            }

            // ----- vídeos -----
            item(key = "videos") {
                Cartao {
                    Text("VÍDEOS POR TREINO", style = Tipos.TituloDeSecao, color = Cores.Texto)
                    if (feedback.treinos.isEmpty()) {
                        Text(
                            text = "Nenhum vídeo gravado nesta semana.",
                            style = Tipos.Corpo,
                            color = Cores.TextoSuave,
                        )
                    }
                    for (treino in feedback.treinos) {
                        val pronto = videosProntos[treino.treino.id]
                        LinhaDoTreino(
                            treino = treino,
                            pronto = pronto,
                            opcoesMudadasEm = opcoes.mudadasEm,
                            // Só a linha do treino que está sendo montado mostra o andamento.
                            porcento = montando?.takeIf { it.treino == treino.treino.nome }?.porcento,
                            ocupado = montando != null,
                            downloadDoClipe = { clipe -> situacao(clipe.chaveDeDownload(), baixando, baixados) },
                            onMontar = { viewModel.montarVideos(planoId, listOf(treino), treinoIds) },
                            onCancelar = viewModel::cancelarMontagem,
                            onVerMontado = {
                                if (pronto != null) {
                                    aberto = VideoAberto(
                                        chave = "treino-${treino.treino.id}-${pronto.lastModified()}",
                                        titulo = treino.treino.nome,
                                        subtitulo = "${quantosClipes(treino.clipes.size)} · ${megabytes(pronto.length())}",
                                        arquivo = pronto,
                                        inicioMs = null,
                                        fimMs = null,
                                        nomeNaGaleria = treino.treino.nome,
                                        podeEnviar = true,
                                    )
                                }
                            },
                            onVerClipe = { clipe -> aberto = clipe.comoVideoAberto(viewModel.arquivoDeVideo(clipe.videoArquivo)) },
                            onBaixarClipe = { clipe ->
                                viewModel.baixarVideo(
                                    chave = clipe.chaveDeDownload(),
                                    arquivo = viewModel.arquivoDeVideo(clipe.videoArquivo),
                                    inicioMs = clipe.registro.corteInicioMs,
                                    fimMs = clipe.registro.corteFimMs,
                                    nome = clipe.exercicio.nome,
                                )
                            },
                        )
                    }
                    if (feedback.treinos.isNotEmpty()) {
                        OpcoesDaEdicao(
                            opcoes = opcoes,
                            ativo = montando == null,
                            onMudar = viewModel::mudarOpcoes,
                            onEscolherMusica = { seletorDeMusica.launch(arrayOf("audio/*")) },
                        )
                        Text(
                            text = "Junta os clipes cortados do treino, com a carga e o RPE na tela. " +
                                "Deixe o app aberto enquanto monta. Toque no vídeo " +
                                "pronto para assistir e enviar só ele; em \"Clipes\", baixe um vídeo " +
                                "específico para a galeria (cortado, com som e sem legenda).",
                            style = Tipos.CorpoPequeno,
                            color = Cores.TextoSuave,
                        )
                    }
                }
            }

            // ----- envio dos vídeos -----
            item(key = "envio") {
                // O físico primeiro, depois um vídeo por treino.
                val arquivos = listOfNotNull(fisico) + videosParaEnviar
                BotaoPrincipal(
                    texto = if (arquivos.isEmpty()) "Enviar vídeos" else "Enviar ${arquivos.size} vídeo(s)",
                    onClick = {
                        // O tipo certo faz o WhatsApp tratar como mídia, e não como documento.
                        enviarArquivos(contexto, arquivos.map(viewModel::uriParaCompartilhar), "video/*")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icone = R.drawable.ic_enviar,
                    ativo = arquivos.isNotEmpty() && montando == null,
                )
            }
        }
    }

    val video = aberto
    if (video != null) {
        TelaDoVideo(
            titulo = video.titulo,
            subtitulo = video.subtitulo,
            arquivo = video.arquivo,
            inicioMs = video.inicioMs,
            fimMs = video.fimMs,
            download = situacao(video.chave, baixando, baixados),
            onBaixar = {
                viewModel.baixarVideo(video.chave, video.arquivo, video.inicioMs, video.fimMs, video.nomeNaGaleria)
            },
            onEnviar = if (video.podeEnviar) {
                { enviarArquivos(contexto, listOf(viewModel.uriParaCompartilhar(video.arquivo)), "video/*") }
            } else {
                null
            },
            onFechar = { aberto = null },
        )
    }
}

/**
 * O vídeo que abre em tela cheia.
 *
 * @param chave identifica o vídeo no download ("salvando", "salvo")
 * @param podeEnviar só o montado é enviado daqui; o clipe cru tem o arquivo inteiro, sem o corte
 */
private data class VideoAberto(
    val chave: String,
    val titulo: String,
    val subtitulo: String?,
    val arquivo: File,
    val inicioMs: Long?,
    val fimMs: Long?,
    val nomeNaGaleria: String,
    val podeEnviar: Boolean,
)

/** Muda quando o clipe muda (corte, carga, vídeo novo), para o "salvo" não valer para a versão antiga. */
private fun ClipeDoTreino.chaveDeDownload(): String = "clipe-${serie.id}-${registro.atualizadoEm}"

private fun ClipeDoTreino.comoVideoAberto(arquivo: File) = VideoAberto(
    chave = chaveDeDownload(),
    titulo = exercicio.nome,
    subtitulo = legendaDoClipe(serie, registro),
    arquivo = arquivo,
    inicioMs = registro.corteInicioMs,
    fimMs = registro.corteFimMs,
    nomeNaGaleria = exercicio.nome,
    podeEnviar = false,
)

private fun situacao(chave: String, baixando: Set<String>, baixados: Set<String>): SituacaoDoDownload = when (chave) {
    in baixando -> SituacaoDoDownload.SALVANDO
    in baixados -> SituacaoDoDownload.SALVO
    else -> SituacaoDoDownload.LIVRE
}

/** O que foi editado no clipe, em palavras curtas: "lento", "congela", "zoom 1,5x". */
private fun marcasDaEdicao(clipe: ClipeDoTreino): List<String> {
    val e = clipe.registro.edicao()
    return buildList {
        if (e.temLento) add("lento")
        if (e.congelarFinal) add("congela")
        if (e.temZoom) add("zoom ${"%.1f".format(e.zoom).replace('.', ',')}x")
        if (e.espelhado) add("espelhado")
    }
}

/**
 * Como os vídeos dos treinos são montados: fade entre os clipes, legenda entrando e o som.
 * Mudar qualquer uma deixa os vídeos já montados como desatualizados.
 */
@Composable
private fun OpcoesDaEdicao(
    opcoes: OpcoesDaMontagem,
    ativo: Boolean,
    onMudar: ((OpcoesDaMontagem) -> OpcoesDaMontagem) -> Unit,
    onEscolherMusica: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Cores.Fundo)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Rotulo("Edição do vídeo")
        ChaveDaMontagem("Transição entre os clipes", opcoes.transicoes, ativo) { v -> onMudar { it.copy(transicoes = v) } }
        ChaveDaMontagem("Legenda entrando com animação", opcoes.legendaAnimada, ativo) { v ->
            onMudar { it.copy(legendaAnimada = v) }
        }
        Text("Som", style = Tipos.CorpoForte, color = Cores.Texto)
        Segmentos(
            opcoes = SomDoVideo.entries.map { it.rotulo },
            selecionada = opcoes.som.ordinal,
            onSelecionar = { i ->
                if (!ativo) return@Segmentos
                val som = SomDoVideo.entries[i]
                // Música sem arquivo ainda: abre o seletor primeiro.
                if (som == SomDoVideo.MUSICA && opcoes.musica == null) onEscolherMusica() else onMudar { it.copy(som = som) }
            },
        )
        when (opcoes.som) {
            SomDoVideo.MUDO -> Text("O vídeo sai sem som.", style = Tipos.CorpoPequeno, color = Cores.TextoSuave)
            SomDoVideo.ORIGINAL -> Text(
                text = "O som que cada clipe gravou. Clipe gravado sem som fica em silêncio.",
                style = Tipos.CorpoPequeno,
                color = Cores.TextoSuave,
            )
            SomDoVideo.MUSICA -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = opcoes.nomeDaMusica ?: "Nenhuma música escolhida",
                    style = Tipos.CorpoPequeno,
                    color = Cores.TextoSuave,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                BotaoContorno("Trocar música", onEscolherMusica, altura = 36.dp, ativo = ativo, fundo = Cores.Superficie)
            }
        }
    }
}

@Composable
private fun ChaveDaMontagem(texto: String, ligado: Boolean, ativo: Boolean, onMudar: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = texto, style = Tipos.Corpo, color = Cores.Texto, modifier = Modifier.weight(1f))
        Switch(
            checked = ligado,
            onCheckedChange = onMudar,
            enabled = ativo,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Cores.Fundo,
                checkedTrackColor = Cores.Destaque,
                uncheckedThumbColor = Cores.TextoSuave,
                uncheckedTrackColor = Cores.Trilho,
                uncheckedBorderColor = Cores.BordaForte,
            ),
        )
    }
}

private fun quantosClipes(n: Int): String = if (n == 1) "1 clipe" else "$n clipes"

@Composable
private fun NumeroDaSemana(rotulo: String, numero: String, total: String, modifier: Modifier = Modifier) {
    Azulejo(modifier = modifier) {
        Rotulo(rotulo)
        Row {
            Text(
                text = numero,
                style = Tipos.NumeroGrande.copy(fontSize = 30.sp, lineHeight = 30.sp),
                color = Cores.Texto,
                modifier = Modifier.alignByBaseline(),
            )
            if (total.isNotEmpty()) {
                Text(
                    text = total,
                    style = Tipos.NumeroMedio.copy(fontSize = 16.sp),
                    color = Cores.TextoSuave,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

/**
 * @param porcento andamento da montagem deste treino, ou nulo se não é ele que está sendo montado
 * @param ocupado verdadeiro enquanto qualquer vídeo está sendo montado
 * @param downloadDoClipe em que pé está o download de cada clipe
 * @param onVerMontado abre o vídeo montado em tela cheia
 */
@Composable
private fun LinhaDoTreino(
    treino: TreinoComClipes,
    pronto: File?,
    opcoesMudadasEm: Long,
    porcento: Int?,
    ocupado: Boolean,
    downloadDoClipe: (ClipeDoTreino) -> SituacaoDoDownload,
    onMontar: () -> Unit,
    onCancelar: () -> Unit,
    onVerMontado: () -> Unit,
    onVerClipe: (ClipeDoTreino) -> Unit,
    onBaixarClipe: (ClipeDoTreino) -> Unit,
) {
    // Mexeu em algum clipe (edição, carga, vídeo novo) ou nas opções da montagem depois
    // de montar: o vídeo ficou para trás.
    val ultimaMudanca = maxOf(treino.clipes.maxOf { it.registro.atualizadoEm }, opcoesMudadasEm)
    val desatualizado = pronto != null && pronto.lastModified() < ultimaMudanca
    val situacao = when {
        porcento != null -> "Montando: $porcento%"
        pronto == null -> "Ainda não montado"
        desatualizado -> "Desatualizado, monte de novo"
        else -> "Pronto · ${megabytes(pronto?.length() ?: 0L)} · toque para ver"
    }
    val podeVer = pronto != null && porcento == null
    var clipesAbertos by rememberSaveable(treino.treino.id) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = podeVer, onClick = onVerMontado),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (podeVer) Cores.NovoFundo else Cores.Trilho),
                    contentAlignment = Alignment.Center,
                ) {
                    if (podeVer) {
                        Icon(
                            painter = painterResource(R.drawable.ic_video),
                            contentDescription = "Ver o vídeo montado",
                            tint = Cores.Destaque,
                            modifier = Modifier.size(22.dp),
                        )
                    } else {
                        Text(
                            text = treino.treino.letra ?: "?",
                            style = Tipos.TituloDeSecao.copy(fontWeight = FontWeight.ExtraBold),
                            color = Cores.Texto,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${treino.treino.nome} · ${quantosClipes(treino.clipes.size)}",
                        style = Tipos.CorpoForte,
                        color = Cores.Texto,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = situacao,
                        style = Tipos.CorpoPequeno,
                        color = if (podeVer && !desatualizado) Cores.FeitoTexto else Cores.TextoSuave,
                    )
                }
            }
            if (porcento != null) {
                BotaoContorno("Cancelar", onCancelar, altura = 40.dp, fundo = Cores.Superficie)
            } else {
                BotaoContorno(
                    texto = if (pronto == null) "Montar" else "Refazer",
                    onClick = onMontar,
                    ativo = !ocupado,
                    altura = 40.dp,
                    fundo = Cores.Superficie,
                )
            }
        }
        if (porcento != null) BarraDeProgresso(porcento / 100f, Cores.Destaque)

        // ----- os clipes, um por um, para assistir ou baixar -----
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { clipesAbertos = !clipesAbertos }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Rotulo(if (clipesAbertos) "Esconder clipes" else "Clipes (${treino.clipes.size})")
            Icon(
                painter = painterResource(R.drawable.ic_abaixo),
                contentDescription = null,
                tint = Cores.TextoSuave,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = if (clipesAbertos) 180f else 0f },
            )
        }
        if (clipesAbertos) {
            for (clipe in treino.clipes) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Cores.Fundo)
                        .clickable { onVerClipe(clipe) }
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconeDeVer()
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = clipe.exercicio.nome,
                            style = Tipos.CorpoForte.copy(fontSize = 14.sp),
                            color = Cores.Texto,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = (listOf(legendaDoClipe(clipe.serie, clipe.registro)) + marcasDaEdicao(clipe))
                                .joinToString(" · "),
                            style = Tipos.CorpoPequeno.copy(fontSize = 12.sp),
                            color = Cores.TextoSuave,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BotaoDeBaixar(downloadDoClipe(clipe)) { onBaixarClipe(clipe) }
                }
            }
        }
    }
}

// ---------- envio para outros apps ----------

private const val WHATSAPP = "com.whatsapp"

private fun copiarTexto(contexto: Context, texto: String) {
    val area = contexto.getSystemService(ClipboardManager::class.java) ?: return
    area.setPrimaryClip(ClipData.newPlainText("Feedback semanal", texto))
}

/** Abre direto no WhatsApp. Sem ele instalado, cai no "Compartilhar" do Android. */
private fun abrirNoWhatsApp(contexto: Context, envio: Intent) {
    try {
        contexto.startActivity(Intent(envio).setPackage(WHATSAPP))
    } catch (e: ActivityNotFoundException) {
        // Sem nenhum app que receba, startActivity lança exceção de novo.
        runCatching { contexto.startActivity(Intent.createChooser(envio, null)) }
    }
}

private fun enviarTexto(contexto: Context, texto: String) {
    val envio = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, texto)
    abrirNoWhatsApp(contexto, envio)
}

/** Manda as fotos e os vídeos de uma vez; no WhatsApp é só escolher a conversa. */
private fun enviarArquivos(contexto: Context, uris: List<Uri>, tipo: String) {
    if (uris.isEmpty()) return
    val envio = Intent(Intent.ACTION_SEND_MULTIPLE)
        .setType(tipo)
        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // Para o WhatsApp conseguir ler os arquivos quando é aberto direto, sem o "Compartilhar".
    envio.clipData = ClipData.newRawUri("Feedback", uris.first()).apply {
        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
    }
    abrirNoWhatsApp(contexto, envio)
}
