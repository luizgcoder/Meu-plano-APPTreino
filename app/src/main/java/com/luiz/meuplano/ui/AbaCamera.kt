package com.luiz.meuplano.ui

import android.content.ActivityNotFoundException
import com.luiz.meuplano.video.EdicaoDoClipe
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.luiz.meuplano.R
import com.luiz.meuplano.video.Captura
import com.luiz.meuplano.video.Marca
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HORA = DateTimeFormatter.ofPattern("HH:mm")
private val DIA_E_MES = DateTimeFormatter.ofPattern("dd/MM")
private val DIA_DA_SEMANA = DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("pt-BR"))

private fun dataDe(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/** "21:40" */
private fun hora(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(HORA)

/** "13/10 · 21:40" */
private fun quando(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM · HH:mm"))

/** "Hoje", "Ontem" ou "13/10" */
private fun tituloDoDia(dia: LocalDate, hoje: LocalDate): String = when (dia) {
    hoje -> "Hoje"
    hoje.minusDays(1) -> "Ontem"
    else -> dia.format(DIA_E_MES)
}

/** "Qui · 15/10" */
private fun legendaDoDia(dia: LocalDate): String =
    dia.format(DIA_DA_SEMANA).trimEnd('.').replaceFirstChar { it.uppercase() } + " · " + dia.format(DIA_E_MES)

/** "0:08" */
private fun duracaoDoVideo(ms: Long): String {
    val s = (ms + 500) / 1000
    return "${s / 60}:%02d".format(s % 60)
}

/**
 * A aba Câmera: fotos e vídeos fora das séries do plano (aquecimento, backoff...), com o
 * mesmo visual da câmera das séries. Ficam numa galeria do próprio app, separada por dia,
 * para rever em câmera lenta, comparar dois vídeos lado a lado, baixar ou enviar.
 *
 * @param onAbrirCamera abre a câmera já com a marca do filtro (Todas grava como Livre)
 */
@Composable
fun AbaCamera(viewModel: PlanoViewModel, onAbrirCamera: (Marca) -> Unit) {
    val capturas by viewModel.capturas.collectAsStateWithLifecycle()
    val processando by viewModel.processandoFoto.collectAsStateWithLifecycle()
    val baixando by viewModel.baixando.collectAsStateWithLifecycle()
    val baixados by viewModel.baixados.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    LaunchedEffect(Unit) { viewModel.atualizarCapturas() }

    // Filtro da galeria: nulo = todas.
    var filtro by rememberSaveable { mutableStateOf<Marca?>(null) }
    var aberta by remember { mutableStateOf<Captura?>(null) }
    // Comparar: escolhe dois vídeos e eles tocam lado a lado.
    var comparando by rememberSaveable { mutableStateOf(false) }
    var escolhidos by remember { mutableStateOf<List<Captura>>(emptyList()) }
    var comparacao by remember { mutableStateOf<Pair<Captura, Captura>?>(null) }
    // O vídeo aberto no corte (só o corte, sem os efeitos das séries).
    var noCorte by remember { mutableStateOf<Captura?>(null) }
    // Foto ou vídeo da galeria do celular, com a marca do filtro de quando tocou.
    var marcaDaImportacao by remember { mutableStateOf(Marca.LIVRE) }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importarCaptura(uri, marcaDaImportacao)
    }
    // Apagar vários: segurar um item entra no modo, depois cada toque marca ou desmarca.
    var apagando by rememberSaveable { mutableStateOf(false) }
    var marcados by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmarApagar by remember { mutableStateOf(false) }

    fun sairDoApagar() {
        apagando = false
        marcados = emptySet()
        confirmarApagar = false
    }

    // Voltar sai da seleção em vez de sair da aba.
    BackHandler(enabled = apagando || comparando) {
        if (apagando) sairDoApagar() else {
            comparando = false
            escolhidos = emptyList()
        }
    }

    val mostradas = capturas.filter { filtro == null || it.marca == filtro }
    // Trocar o filtro limpa a seleção, para não apagar o que nem está na tela.
    LaunchedEffect(filtro) { if (apagando) sairDoApagar() }
    val hoje = LocalDate.now()
    val porDia = remember(mostradas) { mostradas.groupBy { dataDe(it.criadaEm) }.toSortedMap(compareByDescending { it }) }
    val videos = capturas.count { !it.foto }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ----- cabeçalho: no Comparar, vira "toque para marcar A e B" -----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = when {
                            apagando -> "TOQUE PARA MARCAR MAIS"
                            comparando -> "TOQUE PARA MARCAR A E B"
                            else -> "${capturas.size} ITENS · $videos VÍDEOS"
                        },
                        style = Tipos.Sobretitulo,
                        color = if (comparando || apagando) Cores.Destaque else Cores.TextoSuave,
                    )
                    Text(
                        text = when {
                            apagando -> if (marcados.size == 1) "1 MARCADO" else "${marcados.size} MARCADOS"
                            comparando -> "COMPARAR"
                            else -> "CÂMERA"
                        },
                        style = Tipos.TituloDeTela,
                        color = Cores.Texto,
                    )
                }
                if (apagando) {
                    Pilula(
                        texto = if (marcados.size == mostradas.size && mostradas.isNotEmpty()) "Desmarcar" else "Todos",
                        fundo = Cores.Superficie,
                        cor = Cores.Texto,
                        borda = Cores.Trilho,
                        altura = 36.dp,
                        onClick = {
                            marcados = if (marcados.size == mostradas.size) emptySet() else mostradas.map { it.arquivo.name }.toSet()
                            confirmarApagar = false
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Pilula(
                    texto = if (comparando || apagando) "Cancelar" else "Comparar",
                    fundo = Cores.Superficie,
                    cor = Cores.Texto,
                    borda = Cores.Trilho,
                    altura = 36.dp,
                    onClick = {
                        if (apagando) {
                            sairDoApagar()
                        } else {
                            comparando = !comparando
                            escolhidos = emptyList()
                        }
                    },
                )
            }

            // ----- filtros com a contagem -----
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf<Marca?>(null) + Marca.entries).forEach { m ->
                    val quantas = capturas.count { m == null || it.marca == m }
                    val escolhida = m == filtro
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(if (escolhida) Cores.Texto else Color.Transparent)
                            .border(1.dp, if (escolhida) Cores.Texto else Cores.Trilho, CircleShape)
                            .clickable { filtro = m }
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val cor = if (escolhida) Cores.Fundo else Cores.Texto
                        Text(text = m?.rotulo ?: "Todas", style = Tipos.Botao, color = cor)
                        Text(text = quantas.toString(), style = Tipos.Botao.copy(fontSize = 12.sp), color = cor.copy(alpha = 0.6f))
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (processando) {
                    item(key = "processando", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(color = Cores.Destaque, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Text("Aplicando o visual na foto…", style = Tipos.CorpoPequeno, color = Cores.TextoSuave)
                        }
                    }
                }

                if (mostradas.isEmpty()) {
                    item(key = "vazio", span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Cores.Superficie),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_camera),
                                    contentDescription = null,
                                    tint = Cores.TextoSuave,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Text(
                                text = "NADA EM ${(filtro?.rotulo ?: "Todas").uppercase()} AINDA",
                                style = Tipos.TituloDeSecao,
                                color = Cores.Texto,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Grave uma série para rever a execução aqui, em câmera lenta.",
                                style = Tipos.Corpo,
                                color = Cores.TextoSuave,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                porDia.forEach { (dia, doDia) ->
                    item(key = "dia-$dia", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = tituloDoDia(dia, hoje).uppercase(),
                                style = Tipos.TituloDeSecao.copy(fontSize = 20.sp),
                                color = Cores.Texto,
                                modifier = Modifier.weight(1f),
                            )
                            Text(text = legendaDoDia(dia), style = Tipos.CorpoPequeno, color = Cores.TextoSuave)
                        }
                    }
                    items(doDia, key = { it.arquivo.name }) { captura ->
                        val ordem = escolhidos.indexOfFirst { it.arquivo == captura.arquivo }
                        // Vídeo com vídeo ou foto com foto, e no máximo dois.
                        val tipo = escolhidos.firstOrNull()?.foto
                        val bloqueada = comparando && ordem < 0 &&
                            (escolhidos.size >= 2 || (tipo != null && tipo != captura.foto))
                        val nome = captura.arquivo.name
                        val marcado = nome in marcados
                        Miniatura(
                            captura = captura,
                            selecionando = comparando || apagando,
                            marcador = when {
                                apagando -> if (marcado) "✓" else null
                                ordem >= 0 -> if (ordem == 0) "A" else "B"
                                else -> null
                            },
                            apagada = bloqueada,
                            onClick = {
                                confirmarApagar = false
                                when {
                                    apagando -> {
                                        marcados = if (marcado) marcados - nome else marcados + nome
                                        if (marcados.isEmpty()) sairDoApagar()
                                    }
                                    !comparando -> aberta = captura
                                    ordem >= 0 -> escolhidos = escolhidos.filterNot { it.arquivo == captura.arquivo }
                                    !bloqueada -> escolhidos = escolhidos + captura
                                }
                            },
                            onSegurar = {
                                // Segurar começa a seleção para apagar (fora do Comparar).
                                if (!comparando) {
                                    apagando = true
                                    marcados = marcados + nome
                                    confirmarApagar = false
                                }
                            },
                        )
                    }
                }
            }
        }

        // ----- Comparar: a barra de baixo com A e B -----
        if (comparando) {
            val tipoFoto = escolhidos.firstOrNull()?.foto
            val (titulo, detalhe) = when (escolhidos.size) {
                0 -> "Escolha 2 itens" to "Vídeo com vídeo ou foto com foto"
                1 -> "1 de 2 · falta o B" to (if (tipoFoto == true) "Escolha outra foto" else "Escolha outro vídeo")
                else -> "A e B prontos" to escolhidos.joinToString(" × ") { hora(it.criadaEm) }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1C1C1F))
                    .border(1.dp, Cores.Trilho, RoundedCornerShape(18.dp))
                    .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(titulo, style = Tipos.CorpoForte, color = Cores.Texto)
                    Text(detalhe, style = Tipos.CorpoPequeno, color = Cores.TextoSuave, maxLines = 1)
                }
                BotaoPrincipal(
                    texto = "COMPARAR",
                    onClick = {
                        comparacao = escolhidos[0] to escolhidos[1]
                        comparando = false
                        escolhidos = emptyList()
                    },
                    ativo = escolhidos.size == 2,
                )
            }
        }

        // ----- apagar os marcados -----
        if (apagando) {
            val quantos = marcados.size
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1C1C1F))
                    .border(1.dp, Cores.Trilho, RoundedCornerShape(18.dp))
                    .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (confirmarApagar) "Apagar de vez?" else if (quantos == 1) "1 item marcado" else "$quantos itens marcados",
                        style = Tipos.CorpoForte,
                        color = Cores.Texto,
                    )
                    Text(
                        text = if (confirmarApagar) "Sai do app. O que já foi baixado continua na galeria." else "Segure ou toque para marcar mais",
                        style = Tipos.CorpoPequeno,
                        color = Cores.TextoSuave,
                        maxLines = 2,
                    )
                }
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (quantos > 0) Cores.Gravar else Cores.Trilho)
                        .clickable(enabled = quantos > 0, onClickLabel = "Apagar os marcados") {
                            if (confirmarApagar) {
                                viewModel.apagarCapturas(capturas.filter { it.arquivo.name in marcados })
                                sairDoApagar()
                            } else {
                                confirmarApagar = true
                            }
                        }
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_lixeira), contentDescription = null, tint = Cores.Fundo, modifier = Modifier.size(20.dp))
                    Text(
                        text = if (confirmarApagar) "SIM, APAGAR" else "APAGAR",
                        style = Tipos.Botao.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                        color = Cores.Fundo,
                    )
                }
            }
        }

        // ----- gravar, com a marca do filtro; ao lado, trazer da galeria do celular -----
        if (!comparando && !apagando) {
            val marca = filtro ?: Marca.LIVRE
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 84.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Cores.Superficie)
                    .border(1.dp, Cores.Trilho, CircleShape)
                    .clickable(onClickLabel = "Trazer foto ou vídeo da galeria para ${marca.rotulo}") {
                        marcaDaImportacao = marca
                        importar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_galeria), contentDescription = null, tint = Cores.Texto, modifier = Modifier.size(22.dp))
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .height(56.dp)
                    .clip(CircleShape)
                    .background(Cores.Destaque)
                    .clickable(onClickLabel = "Abrir câmera para ${marca.rotulo}") { onAbrirCamera(marca) }
                    .padding(start = 16.dp, end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(2.5.dp, Cores.Fundo, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Cores.Fundo),
                    )
                }
                Text(
                    text = if (filtro == null) "Gravar" else "Gravar · ${marca.rotulo}",
                    style = Tipos.Botao.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                    color = Cores.Fundo,
                )
            }
        }
    }

    val par = comparacao
    if (par != null) {
        TelaComparar(
            primeiro = par.first,
            segundo = par.second,
            onFechar = { comparacao = null },
        )
    }

    val atual = aberta
    if (atual != null) {
        val chave = "camera-${atual.arquivo.name}"
        val download = when (chave) {
            in baixando -> SituacaoDoDownload.SALVANDO
            in baixados -> SituacaoDoDownload.SALVO
            else -> SituacaoDoDownload.LIVRE
        }
        val apagar = {
            viewModel.apagarCaptura(atual)
            aberta = null
        }
        val enviar = { compartilhar(contexto, viewModel.uriParaCompartilhar(atual.arquivo), atual.foto) }
        if (atual.foto) {
            TelaDaFoto(
                titulo = atual.marca.rotulo,
                subtitulo = quando(atual.criadaEm),
                arquivo = atual.arquivo,
                download = download,
                onBaixar = { viewModel.baixarFoto(chave, atual.arquivo, atual.marca.rotulo) },
                onEnviar = enviar,
                onApagar = apagar,
                onFechar = { aberta = null },
            )
        } else {
            TelaDoVideo(
                titulo = atual.marca.rotulo,
                subtitulo = quando(atual.criadaEm),
                arquivo = atual.arquivo,
                inicioMs = null,
                fimMs = null,
                download = download,
                onBaixar = { viewModel.baixarVideo(chave, atual.arquivo, null, null, atual.marca.rotulo) },
                onEnviar = enviar,
                onFechar = { aberta = null },
                onApagar = apagar,
                onCortar = {
                    aberta = null
                    noCorte = atual
                },
            )
        }
    }

    val cortar = noCorte
    if (cortar != null) {
        Dialog(
            onDismissRequest = { noCorte = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            TelaCorte(
                titulo = cortar.marca.rotulo,
                arquivo = cortar.arquivo,
                edicaoSalva = EdicaoDoClipe(),
                onSalvar = { edicao ->
                    viewModel.cortarCaptura(cortar, edicao.inicioMs, edicao.fimMs)
                    noCorte = null
                },
                onRegravar = null,
                onFechar = { noCorte = null },
                soCorte = true,
            )
        }
    }
}

/** O que a miniatura mostra: a imagem e, no vídeo, a duração. */
private class Previa(val imagem: ImageBitmap?, val duracaoMs: Long?)

/**
 * Um quadro da galeria: a foto, ou o primeiro quadro do vídeo com a duração.
 *
 * @param selecionando no Comparar ou no apagar: mostra o círculo de marcar
 * @param marcador o que vai no círculo: "A" / "B" no Comparar, "✓" no apagar; nulo = vazio
 * @param apagada no Comparar, o que não pode mais ser escolhido fica apagado
 * @param onSegurar segurar o item (começa a seleção para apagar)
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Miniatura(
    captura: Captura,
    selecionando: Boolean,
    marcador: String?,
    apagada: Boolean,
    onClick: () -> Unit,
    onSegurar: () -> Unit,
) {
    val haptico = LocalHapticFeedback.current
    // A data do arquivo na chave: depois de cortado, o mesmo arquivo tem outra duração.
    val previa by produceState(Previa(null, null), captura.arquivo, captura.arquivo.lastModified()) {
        value = withContext(Dispatchers.IO) { lerPrevia(captura) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .background(Cores.Superficie)
            .graphicsLayer { alpha = if (apagada) 0.35f else 1f }
            .combinedClickable(
                onClickLabel = (if (captura.foto) "Foto" else "Vídeo") + " das " + hora(captura.criadaEm),
                onLongClickLabel = "Marcar para apagar",
                onLongClick = {
                    haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSegurar()
                },
                onClick = onClick,
            ),
    ) {
        previa.imagem?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(
            text = hora(captura.criadaEm),
            style = Tipos.CorpoPequeno.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, shadow = SOMBRA),
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
        )
        if (!captura.foto) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("▶", style = Tipos.CorpoPequeno.copy(fontSize = 9.sp), color = Color.White)
                Text(
                    text = previa.duracaoMs?.let(::duracaoDoVideo) ?: "",
                    style = Tipos.CorpoPequeno.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
        val escolhida = marcador
        if (escolhida != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, if (escolhida == "✓") Cores.Gravar else Cores.Destaque),
            )
        }
        if (selecionando) {
            // O círculo de marcar: vazio, ou com A / B / ✓.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            escolhida == "✓" -> Cores.Gravar
                            escolhida != null -> Cores.Destaque
                            else -> Color.Black.copy(alpha = 0.35f)
                        },
                    )
                    .then(
                        if (escolhida == null) Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape) else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (escolhida != null) {
                    Text(escolhida, style = Tipos.NumeroMedio.copy(fontSize = 15.sp), color = Cores.Fundo)
                }
            }
        }
    }
}

private val SOMBRA = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 1f), 4f)

/** A miniatura e, nos vídeos, a duração. */
private fun lerPrevia(captura: Captura): Previa {
    if (captura.foto) {
        val opcoes = BitmapFactory.Options().apply { inSampleSize = 8 }
        val foto = runCatching { BitmapFactory.decodeFile(captura.arquivo.absolutePath, opcoes) }.getOrNull()
        return Previa(foto?.let { reduzir(it, 360) }, null)
    }
    val leitor = MediaMetadataRetriever()
    return try {
        leitor.setDataSource(captura.arquivo.absolutePath)
        val duracao = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        val quadro = leitor.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        Previa(quadro?.let { reduzir(it, 360) }, duracao)
    } catch (e: Exception) {
        Previa(null, null)
    } finally {
        runCatching { leitor.release() }
    }
}

private fun reduzir(imagem: Bitmap, altura: Int): ImageBitmap {
    val largura = (imagem.width * altura / imagem.height.coerceAtLeast(1)).coerceAtLeast(1)
    val pequena = Bitmap.createScaledBitmap(imagem, largura, altura, true)
    if (pequena !== imagem) imagem.recycle()
    return pequena.asImageBitmap()
}

/** A foto aberta em tela cheia, com baixar, enviar e apagar. */
@Composable
private fun TelaDaFoto(
    titulo: String,
    subtitulo: String,
    arquivo: File,
    download: SituacaoDoDownload,
    onBaixar: () -> Unit,
    onEnviar: () -> Unit,
    onApagar: () -> Unit,
    onFechar: () -> Unit,
) {
    var confirmarApagar by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotaoQuadrado(R.drawable.ic_fechar, "Fechar", onFechar, fundo = Cores.Superficie)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = titulo.uppercase(), style = Tipos.TituloDeSecao, color = Cores.Texto)
                    Text(text = subtitulo, style = Tipos.CorpoPequeno, color = Cores.TextoSuave)
                }
                BotaoContorno(
                    texto = if (confirmarApagar) "Apagar mesmo?" else "Apagar",
                    onClick = { if (confirmarApagar) onApagar() else confirmarApagar = true },
                    altura = 40.dp,
                    fundo = Cores.Superficie,
                )
            }
            AsyncImage(
                model = arquivo,
                contentDescription = "Foto",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotaoContorno(
                    texto = when (download) {
                        SituacaoDoDownload.LIVRE -> "Baixar"
                        SituacaoDoDownload.SALVANDO -> "Salvando…"
                        SituacaoDoDownload.SALVO -> "Salva na galeria"
                    },
                    onClick = onBaixar,
                    modifier = Modifier.weight(1f),
                    icone = if (download == SituacaoDoDownload.SALVO) R.drawable.ic_feito else R.drawable.ic_baixar,
                    ativo = download != SituacaoDoDownload.SALVANDO,
                    altura = 48.dp,
                    fundo = Cores.Superficie,
                )
                BotaoPrincipal(
                    texto = "Enviar",
                    onClick = onEnviar,
                    modifier = Modifier.weight(1f),
                    icone = R.drawable.ic_enviar,
                )
            }
        }
    }
}

/** Abre o "Compartilhar" do Android para um arquivo só: WhatsApp, Drive, o que tiver. */
private fun compartilhar(contexto: Context, uri: Uri, foto: Boolean) {
    val envio = Intent(Intent.ACTION_SEND)
        .setType(if (foto) "image/jpeg" else "video/mp4")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    envio.clipData = ClipData.newRawUri("Captura", uri)
    try {
        contexto.startActivity(Intent.createChooser(envio, null))
    } catch (e: ActivityNotFoundException) {
        // Sem nenhum app que receba: não há o que fazer.
    }
}
