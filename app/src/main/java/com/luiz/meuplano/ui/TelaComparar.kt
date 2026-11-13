package com.luiz.meuplano.ui

import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.luiz.meuplano.R
import com.luiz.meuplano.video.Captura
import androidx.compose.ui.text.style.TextOverflow
import com.luiz.meuplano.video.UM_QUADRO_MS
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Como os dois aparecem. */
private enum class Arranjo(val rotulo: String) {
    LADO("Lado a lado"),
    EMPILHADO("Empilhado"),
    FANTASMA("Fantasma"),
}

private val VELOCIDADES = listOf(0.25f to "¼×", 0.5f to "½×", 1f to "1×")
private val OPACIDADES = listOf(0.3f to "30%", 0.5f to "50%", 0.75f to "75%")

/** Passo do ajuste de sincronia de cada vídeo. */
private const val PASSO_DA_SINCRONIA_MS = 100L

/** Diferença entre os dois vídeos a partir da qual o B é puxado de volta para o A. */
private const val TOLERANCIA_MS = 150

/**
 * Um tocador para o Comparar. Usa TextureView (e não VideoView) porque o modo fantasma
 * põe um vídeo transparente por cima do outro, e só a TextureView aceita transparência.
 * Sem som: são dois vídeos ao mesmo tempo.
 */
@Stable
private class Tocador(arquivo: File) {
    val mp = MediaPlayer()
    var pronto by mutableStateOf(false)
    var duracaoMs by mutableIntStateOf(0)

    init {
        runCatching {
            mp.setDataSource(arquivo.absolutePath)
            mp.setVolume(0f, 0f)
            mp.setOnPreparedListener {
                duracaoMs = it.duration.coerceAtLeast(0)
                pronto = true
            }
            mp.prepareAsync()
        }
    }

    val posicao: Int get() = if (pronto) runCatching { mp.currentPosition }.getOrDefault(0) else 0

    fun irPara(ms: Long) {
        if (pronto) runCatching { mp.seekTo(ms.coerceIn(0L, duracaoMs.toLong()), MediaPlayer.SEEK_CLOSEST) }
    }

    fun tocar(velocidade: Float) {
        if (!pronto) return
        runCatching { mp.start() }
        // Só com o vídeo tocando: mudar a velocidade parado faria ele voltar a tocar.
        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(velocidade) }
            .onFailure { runCatching { mp.playbackParams = mp.playbackParams.setSpeed(0.5f) } }
    }

    fun pausar() {
        if (pronto) runCatching { if (mp.isPlaying) mp.pause() }
    }

    fun soltar() {
        runCatching { mp.release() }
    }
}

/** Largura sobre altura do vídeo como ele aparece (já girado). */
internal fun proporcaoDoVideo(arquivo: File): Float {
    val leitor = MediaMetadataRetriever()
    return try {
        leitor.setDataSource(arquivo.absolutePath)
        val l = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: return 9f / 16f
        val a = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: return 9f / 16f
        val giro = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (giro % 180 != 0) a / l else l / a
    } catch (e: Exception) {
        9f / 16f
    } finally {
        runCatching { leitor.release() }
    }
}

/** "00:03,2" */
private fun tempoComDecimo(ms: Long): String {
    val d = ms.coerceAtLeast(0) / 100
    return "%02d:%02d,%d".format(d / 600, (d / 10) % 60, d % 10)
}

/** "início 0,0s", "+0,3s" */
private fun textoDoInicio(ms: Long): String =
    if (ms == 0L) "início 0,0s" else "+" + "%.1f".format(ms / 1000f).replace('.', ',') + "s"

/**
 * Compara duas capturas: dois vídeos tocando juntos ou duas fotos. Lado a lado, empilhado
 * ou fantasma (o B transparente por cima do A). Nos vídeos, cada um tem o ponto de início
 * ajustável, para os dois começarem no mesmo momento do movimento, e o controle (play,
 * quadro a quadro, velocidade, repetir) vale para os dois.
 */
@Composable
fun TelaComparar(primeiro: Captura, segundo: Captura, onFechar: () -> Unit) {
    val video = !primeiro.foto
    val mesmaMarca = primeiro.marca == segundo.marca
    TelaComparar(
        primeiro = LadoDoComparar(primeiro.arquivo, primeiro.foto, quandoCurto(primeiro.criadaEm)),
        segundo = LadoDoComparar(segundo.arquivo, segundo.foto, quandoCurto(segundo.criadaEm)),
        sobretitulo = (if (mesmaMarca) primeiro.marca.rotulo + " · " else "") + if (video) "2 vídeos" else "2 fotos",
        onFechar = onFechar,
    )
}

/**
 * Um dos dois lados da comparação.
 *
 * @param quando a etiqueta curta do painel ("Qua 14/10 · 20:56", "Sem 3 · 100 kg")
 * @param inicioMs onde o vídeo começa ao abrir (o corte da série, por exemplo)
 */
data class LadoDoComparar(
    val arquivo: File,
    val foto: Boolean,
    val quando: String,
    val inicioMs: Long = 0L,
)

@Composable
fun TelaComparar(
    primeiro: LadoDoComparar,
    segundo: LadoDoComparar,
    sobretitulo: String,
    onFechar: () -> Unit,
    titulo: String = "COMPARAR",
) {
    var trocado by remember { mutableStateOf(false) }
    val a = if (trocado) segundo else primeiro
    val b = if (trocado) primeiro else segundo
    val video = !primeiro.foto

    var arranjo by remember { mutableStateOf(Arranjo.LADO) }
    var opacidade by remember { mutableFloatStateOf(0.5f) }

    Dialog(onDismissRequest = onFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Cores.Fundo)
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ----- topo -----
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotaoQuadrado(R.drawable.ic_anterior, "Voltar", onFechar)
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = sobretitulo,
                        style = Tipos.Rotulo,
                        color = Cores.TextoSuave,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = titulo,
                        style = Tipos.TituloDeTela.copy(fontSize = 28.sp, lineHeight = 28.sp),
                        color = Cores.Texto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BotaoQuadrado(R.drawable.ic_trocar, "Trocar A e B de lugar", { trocado = !trocado })
            }

            Segmentos(
                opcoes = Arranjo.entries.map { it.rotulo },
                selecionada = arranjo.ordinal,
                onSelecionar = { arranjo = Arranjo.entries[it] },
            )

            if (video) {
                ComparacaoDeVideos(a, b, arranjo, opacidade, onOpacidade = { opacidade = it }, modifier = Modifier.weight(1f))
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    Paineis(
                        arranjo = arranjo,
                        opacidade = opacidade,
                        onOpacidade = { opacidade = it },
                        quandoA = a.quando,
                        quandoB = b.quando,
                        conteudoA = { mod -> AsyncImage(model = a.arquivo, contentDescription = "Foto A", contentScale = ContentScale.Fit, modifier = mod) },
                        conteudoB = { mod -> AsyncImage(model = b.arquivo, contentDescription = "Foto B", contentScale = ContentScale.Fit, modifier = mod) },
                    )
                }
            }
        }
    }
}

/** "Qua 14/10 · 20:56" */
fun quandoCurto(criadaEm: Long): String {
    val momento = java.time.Instant.ofEpochMilli(criadaEm).atZone(java.time.ZoneId.systemDefault())
    val dia = momento.format(java.time.format.DateTimeFormatter.ofPattern("EEE dd/MM", java.util.Locale.forLanguageTag("pt-BR")))
    return dia.replace(".", "").replaceFirstChar { it.uppercase() } + " · " + momento.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}

/**
 * Os dois painéis no arranjo escolhido. No fantasma, o B vai por cima do A com a
 * transparência escolhida.
 *
 * @param extraA o que vai por cima de cada painel (o ajuste de início e a barrinha), ou nada
 */
@Composable
private fun Paineis(
    arranjo: Arranjo,
    opacidade: Float,
    onOpacidade: (Float) -> Unit,
    quandoA: String,
    quandoB: String,
    conteudoA: @Composable (Modifier) -> Unit,
    conteudoB: @Composable (Modifier) -> Unit,
    extraA: (@Composable BoxScope.() -> Unit)? = null,
    extraB: (@Composable BoxScope.() -> Unit)? = null,
) {
    val canto = RoundedCornerShape(14.dp)
    @Composable
    fun Painel(letra: String, quando: String, corDaLetra: Color, modifier: Modifier, conteudo: @Composable (Modifier) -> Unit, extra: (@Composable BoxScope.() -> Unit)?) {
        Box(
            modifier = modifier
                .clip(canto)
                .background(Cores.Superficie),
            contentAlignment = Alignment.Center,
        ) {
            conteudo(Modifier.fillMaxSize())
            Etiqueta(letra, quando, corDaLetra, Modifier.align(Alignment.TopStart).padding(8.dp))
            extra?.invoke(this)
        }
    }
    when (arranjo) {
        Arranjo.LADO -> Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Painel("A", quandoA, Cores.Texto, Modifier.weight(1f).fillMaxSize(), conteudoA, extraA)
            Painel("B", quandoB, Cores.Destaque, Modifier.weight(1f).fillMaxSize(), conteudoB, extraB)
        }
        Arranjo.EMPILHADO -> Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Painel("A", quandoA, Cores.Texto, Modifier.weight(1f).fillMaxWidth(), conteudoA, extraA)
            Painel("B", quandoB, Cores.Destaque, Modifier.weight(1f).fillMaxWidth(), conteudoB, extraB)
        }
        Arranjo.FANTASMA -> Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(canto)
                .background(Cores.Superficie),
            contentAlignment = Alignment.Center,
        ) {
            conteudoA(Modifier.fillMaxSize())
            conteudoB(Modifier.fillMaxSize().graphicsLayer { alpha = opacidade })
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(10.dp),
            ) {
                Text("A · $quandoA", style = Tipos.CorpoPequeno.copy(fontWeight = FontWeight.Bold), color = Color.White, modifier = Modifier.weight(1f))
                Text("B por cima · $quandoB", style = Tipos.CorpoPequeno.copy(fontWeight = FontWeight.Bold), color = Cores.Destaque)
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Rotulo("Transparência do B", cor = Color(0xFFD4D4D8))
                Segmentos(
                    opcoes = OPACIDADES.map { it.second },
                    selecionada = OPACIDADES.indexOfFirst { it.first == opacidade }.takeIf { it >= 0 },
                    onSelecionar = { onOpacidade(OPACIDADES[it].first) },
                )
            }
        }
    }
}

/** "A · Qua 14/10 · 20:56" no canto do painel. */
@Composable
private fun Etiqueta(letra: String, quando: String, cor: Color, modifier: Modifier) {
    Row(
        modifier = modifier
            .height(26.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(start = 4.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(cor),
            contentAlignment = Alignment.Center,
        ) {
            Text(letra, style = Tipos.NumeroMedio.copy(fontSize = 14.sp), color = Cores.Fundo)
        }
        Text(quando, style = Tipos.CorpoPequeno.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1)
    }
}

/** Os dois vídeos e o controle que vale para os dois. */
@Composable
private fun ComparacaoDeVideos(
    a: LadoDoComparar,
    b: LadoDoComparar,
    arranjo: Arranjo,
    opacidade: Float,
    onOpacidade: (Float) -> Unit,
    modifier: Modifier,
) {
    val tocadorA = remember(a.arquivo) { Tocador(a.arquivo) }
    val tocadorB = remember(b.arquivo) { Tocador(b.arquivo) }
    DisposableEffect(tocadorA) { onDispose { tocadorA.soltar() } }
    DisposableEffect(tocadorB) { onDispose { tocadorB.soltar() } }

    val proporcaoA by produceState(9f / 16f, a.arquivo) { value = withContext(Dispatchers.IO) { proporcaoDoVideo(a.arquivo) } }
    val proporcaoB by produceState(9f / 16f, b.arquivo) { value = withContext(Dispatchers.IO) { proporcaoDoVideo(b.arquivo) } }

    // Onde cada vídeo começa (para alinhar o movimento) e o tempo comum, a partir desse início.
    var inicioA by remember(a.arquivo) { mutableLongStateOf(a.inicioMs.coerceAtLeast(0L)) }
    var inicioB by remember(b.arquivo) { mutableLongStateOf(b.inicioMs.coerceAtLeast(0L)) }
    var t by remember { mutableLongStateOf(0L) }
    var tocando by remember { mutableStateOf(false) }
    var velocidade by remember { mutableFloatStateOf(0.5f) }
    var repetir by remember { mutableStateOf(true) }

    val prontos = tocadorA.pronto && tocadorB.pronto
    val duracao = if (prontos) {
        minOf(tocadorA.duracaoMs - inicioA, tocadorB.duracaoMs - inicioB).coerceAtLeast(0L)
    } else {
        0L
    }

    fun posicionar(novo: Long) {
        t = novo.coerceIn(0L, duracao)
        tocadorA.irPara(inicioA + t)
        tocadorB.irPara(inicioB + t)
    }

    // Parados: mostra o primeiro quadro de cada um no início escolhido.
    LaunchedEffect(prontos, inicioA, inicioB) {
        if (prontos && !tocando) posicionar(t)
    }

    // Tocando: o A manda no tempo e o B é puxado de volta se escorregar.
    LaunchedEffect(tocando, velocidade, prontos) {
        if (!prontos) return@LaunchedEffect
        if (!tocando) {
            tocadorA.pausar()
            tocadorB.pausar()
            return@LaunchedEffect
        }
        if (t >= duracao) posicionar(0L) else posicionar(t)
        tocadorA.tocar(velocidade)
        tocadorB.tocar(velocidade)
        while (true) {
            delay(40)
            t = (tocadorA.posicao - inicioA).toLong().coerceAtLeast(0L)
            val esperado = inicioB + t
            if (abs(tocadorB.posicao - esperado) > TOLERANCIA_MS) tocadorB.irPara(esperado)
            if (t >= duracao) {
                if (repetir) {
                    posicionar(0L)
                } else {
                    tocando = false
                }
            }
        }
    }

    fun passo(sentido: Int) {
        tocando = false
        posicionar(t + sentido * UM_QUADRO_MS)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            Paineis(
                arranjo = arranjo,
                opacidade = opacidade,
                onOpacidade = onOpacidade,
                quandoA = a.quando,
                quandoB = b.quando,
                conteudoA = { mod -> ImagemDoTocador(tocadorA, proporcaoA, mod) },
                conteudoB = { mod -> ImagemDoTocador(tocadorB, proporcaoB, mod) },
                extraA = {
                    AjusteDoInicio(
                        inicio = inicioA,
                        progresso = if (duracao > 0) t.toFloat() / duracao else 0f,
                        cor = Cores.Texto,
                        letra = "A",
                        onMudar = { inicioA = it.coerceIn(0L, (tocadorA.duracaoMs - 500L).coerceAtLeast(0L)) },
                    )
                },
                extraB = {
                    AjusteDoInicio(
                        inicio = inicioB,
                        progresso = if (duracao > 0) t.toFloat() / duracao else 0f,
                        cor = Cores.Destaque,
                        letra = "B",
                        onMudar = { inicioB = it.coerceIn(0L, (tocadorB.duracaoMs - 500L).coerceAtLeast(0L)) },
                    )
                },
            )
        }

        // ----- controle dos dois -----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Cores.Superficie)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Slider(
                value = if (duracao > 0) t.toFloat() / duracao else 0f,
                onValueChange = {
                    tocando = false
                    posicionar((it * duracao).toLong())
                },
                enabled = prontos && duracao > 0,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Cores.Destaque,
                    inactiveTrackColor = Cores.Trilho,
                ),
            )
            Row {
                Text(tempoComDecimo(t), style = Tipos.CorpoPequeno.copy(fontWeight = FontWeight.SemiBold), color = Cores.TextoSuave, modifier = Modifier.weight(1f))
                Text(tempoComDecimo(duracao), style = Tipos.CorpoPequeno.copy(fontWeight = FontWeight.SemiBold), color = Cores.TextoSuave)
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    VELOCIDADES.forEach { (v, rotulo) ->
                        val escolhida = v == velocidade
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (escolhida) Cores.Texto else Color.Transparent)
                                .clickable { velocidade = v }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(rotulo, style = Tipos.Botao.copy(fontSize = 12.sp), color = if (escolhida) Cores.Fundo else Color(0xFFD4D4D8))
                        }
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BotaoDeQuadro("◂", "Quadro anterior") { passo(-1) }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Cores.Texto)
                            .clickable(enabled = prontos, onClickLabel = if (tocando) "Pausar os dois" else "Tocar os dois") { tocando = !tocando },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (tocando) "❚❚" else "▶", style = Tipos.Botao.copy(fontSize = 20.sp), color = Cores.Fundo)
                    }
                    BotaoDeQuadro("▸", "Próximo quadro") { passo(1) }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (repetir) Cores.Texto else Color.Transparent)
                        .clickable(onClickLabel = "Repetir") { repetir = !repetir },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_virar),
                        contentDescription = "Repetir",
                        tint = if (repetir) Cores.Fundo else Color(0xFFD4D4D8),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BotaoDeQuadro(simbolo: String, descricao: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = descricao, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(simbolo, style = Tipos.Botao.copy(fontSize = 22.sp), color = Cores.Texto)
    }
}

/** O − / início / + e a barrinha de cada vídeo, na base do painel. */
@Composable
private fun BoxScope.AjusteDoInicio(inicio: Long, progresso: Float, cor: Color, letra: String, onMudar: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClickLabel = "Começar $letra antes") { onMudar(inicio - PASSO_DA_SINCRONIA_MS) },
                contentAlignment = Alignment.Center,
            ) { Text("−", style = Tipos.Botao.copy(fontSize = 18.sp), color = Cores.Texto) }
            Text(
                text = textoDoInicio(inicio),
                style = Tipos.CorpoPequeno.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = Cores.Texto,
                modifier = Modifier.widthIn(min = 56.dp),
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClickLabel = "Começar $letra depois") { onMudar(inicio + PASSO_DA_SINCRONIA_MS) },
                contentAlignment = Alignment.Center,
            ) { Text("+", style = Tipos.Botao.copy(fontSize = 18.sp), color = Cores.Texto) }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progresso.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(cor),
            )
        }
    }
}

/** A imagem de um tocador, no formato certo e centrada no painel. */
@Composable
private fun ImagemDoTocador(tocador: Tocador, proporcao: Float, modifier: Modifier) {
    val contexto = LocalContext.current
    val tela = remember(tocador) {
        TextureView(contexto).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                private var superficie: Surface? = null

                override fun onSurfaceTextureAvailable(textura: SurfaceTexture, largura: Int, altura: Int) {
                    val nova = Surface(textura)
                    superficie = nova
                    runCatching { tocador.mp.setSurface(nova) }
                }

                override fun onSurfaceTextureSizeChanged(textura: SurfaceTexture, largura: Int, altura: Int) = Unit

                override fun onSurfaceTextureDestroyed(textura: SurfaceTexture): Boolean {
                    runCatching { tocador.mp.setSurface(null) }
                    superficie?.release()
                    superficie = null
                    return true
                }

                override fun onSurfaceTextureUpdated(textura: SurfaceTexture) = Unit
            }
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { tela },
            modifier = Modifier.aspectRatio(proporcao),
        )
    }
}
