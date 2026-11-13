package com.luiz.meuplano.ui

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.luiz.meuplano.R
import com.luiz.meuplano.video.CONGELAMENTO_MS
import com.luiz.meuplano.video.EdicaoDoClipe
import com.luiz.meuplano.video.UM_QUADRO_MS
import com.luiz.meuplano.video.ZOOM_MAXIMO
import com.luiz.meuplano.video.duracaoDoClipeNaSaidaMs
import com.luiz.meuplano.video.limitarCentro
import com.luiz.meuplano.video.temposDasMiniaturas
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Menor trecho que dá para deixar, em milissegundos. */
private const val TRECHO_MINIMO = 500f

/** Menor trecho em câmera lenta. */
private const val LENTO_MINIMO = 200f

/** Folga para considerar que a alça está na ponta do vídeo. */
private const val FOLGA_DA_PONTA = 60f

/** Quantas miniaturas na faixa do vídeo. */
private const val MINIATURAS = 10

/** Maior lado de cada miniatura, em pixels. */
private const val LADO_DA_MINIATURA = 160

/** Uma busca do tocador que não responde nesse tempo é dada como perdida. */
private const val BUSCA_PERDIDA_MS = 1_500L

/** As velocidades de câmera lenta que dá para escolher. */
private val VELOCIDADES = listOf(0.25f, 0.5f, 0.75f)

/** "¼×", "½×", "¾×" */
private fun rotuloDaVelocidade(v: Float): String = when {
    v <= 0.3f -> "¼×"
    v <= 0.6f -> "½×"
    else -> "¾×"
}

/** [v] dentro de [a]..[b]; se a faixa ficou vazia (vídeo curto demais), fica em [a]. */
private fun limitar(v: Float, a: Float, b: Float): Float = if (b < a) a else v.coerceIn(a, b)

/** Qual ponto o "quadro a quadro" mexe. */
private enum class Alca(val rotulo: String) {
    INICIO("Início"),
    FIM("Fim"),
    LENTO_INICIO("Lento ▸"),
    LENTO_FIM("◂ Lento"),
}

/** As ferramentas, uma de cada vez embaixo do vídeo. */
private enum class Painel(val rotulo: String) {
    CORTE("Corte"),
    LENTO("Lento"),
    ZOOM("Zoom"),
    MAIS("Mais"),
}

/**
 * O tocador da tela de corte: MediaPlayer numa TextureView (e não VideoView).
 *
 * - A TextureView aceita espelhar e ampliar, então a prévia mostra o vídeo como ele vai sair.
 * - O tocador não é refeito quando o app vai para segundo plano: a edição em andamento
 *   continua lá na volta.
 * - As buscas não se acumulam: enquanto uma está em andamento só a última pedida é guardada,
 *   e arrastar a alça não fica atrasado em relação ao dedo.
 * - Sem som, para não pausar a música que estiver tocando no celular.
 */
@Stable
private class TocadorDoCorte(arquivo: File) {
    private val mp = MediaPlayer()
    private var solto = false
    private var buscandoDesde = 0L
    private var proximaBusca: Long? = null

    var pronto by mutableStateOf(false)
        private set
    var erro by mutableStateOf(false)
        private set
    var duracaoMs by mutableIntStateOf(0)
        private set

    /** O vídeo chegou ao fim do arquivo (e parou sozinho). */
    var terminou = false
        private set

    init {
        runCatching {
            mp.setDataSource(arquivo.absolutePath)
            mp.setVolume(0f, 0f)
            mp.setOnPreparedListener {
                duracaoMs = it.duration.coerceAtLeast(0)
                pronto = true
            }
            mp.setOnSeekCompleteListener {
                val proxima = proximaBusca
                proximaBusca = null
                buscandoDesde = 0L
                if (proxima != null) buscar(proxima)
            }
            mp.setOnCompletionListener { terminou = true }
            mp.setOnErrorListener { _, _, _ ->
                erro = true
                true
            }
            mp.prepareAsync()
        }.onFailure { erro = true }
    }

    /** Verdadeiro enquanto o tocador ainda está indo para o ponto pedido. */
    val buscando: Boolean
        get() = buscandoDesde != 0L && SystemClock.uptimeMillis() - buscandoDesde < BUSCA_PERDIDA_MS

    val posicao: Int
        get() = if (pronto && !solto) runCatching { mp.currentPosition }.getOrDefault(0) else 0

    /** Vai para o quadro exato (e não para o quadro-chave mais próximo). */
    fun irPara(ms: Long) {
        if (!pronto || solto) return
        val alvo = ms.coerceIn(0L, duracaoMs.toLong())
        terminou = false
        if (buscando) proximaBusca = alvo else buscar(alvo)
    }

    private fun buscar(ms: Long) {
        buscandoDesde = SystemClock.uptimeMillis()
        runCatching { mp.seekTo(ms, MediaPlayer.SEEK_CLOSEST) }.onFailure { buscandoDesde = 0L }
    }

    fun tocar() {
        if (pronto && !solto) runCatching { mp.start() }
    }

    fun pausar() {
        if (pronto && !solto) runCatching { if (mp.isPlaying) mp.pause() }
    }

    /** Só com o vídeo tocando: mudar a velocidade com ele parado faria ele voltar a tocar. */
    fun velocidade(v: Float) {
        if (pronto && !solto) runCatching { mp.playbackParams = mp.playbackParams.setSpeed(v) }
    }

    /** Liga (ou desliga, com nulo) a tela em que o vídeo aparece. */
    fun ligarTela(superficie: Surface?) {
        if (solto) return
        runCatching { mp.setSurface(superficie) }
        // Tela nova com o vídeo parado: busca de novo o mesmo ponto, para o quadro aparecer.
        if (superficie != null && pronto && runCatching { !mp.isPlaying }.getOrDefault(false)) irPara(posicao.toLong())
    }

    fun soltar() {
        solto = true
        runCatching { mp.release() }
    }
}

/**
 * Assistir e editar o vídeo de uma série: o corte (alças na faixa de miniaturas e ajuste
 * quadro a quadro), o trecho em câmera lenta, o zoom, espelhar e congelar o último quadro.
 *
 * Com [soCorte] (vídeos da aba Câmera) fica só o corte; sem [onRegravar], sem o Regravar.
 *
 * O arquivo não é alterado: só a edição é guardada, e ela é aplicada quando o vídeo
 * do treino é montado. A prévia toca o trecho em repetição já como ele vai sair: com o
 * zoom, espelhado, devagar no lento e parando no fim se o final congela.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaCorte(
    titulo: String,
    arquivo: File,
    edicaoSalva: EdicaoDoClipe,
    onSalvar: (EdicaoDoClipe) -> Unit,
    onRegravar: (() -> Unit)?,
    onFechar: () -> Unit,
    soCorte: Boolean = false,
) {
    // Tudo em milissegundos. duracao = 0 enquanto o vídeo não carregou.
    var duracao by remember { mutableIntStateOf(0) }
    var inicio by remember { mutableFloatStateOf(0f) }
    var fim by remember { mutableFloatStateOf(0f) }
    var comLento by remember { mutableStateOf(edicaoSalva.temLento) }
    var lentoInicio by remember { mutableFloatStateOf(0f) }
    var lentoFim by remember { mutableFloatStateOf(0f) }
    var velocidadeLenta by remember {
        // Uma das três velocidades da tela: a mais próxima da que estava salva.
        mutableFloatStateOf(VELOCIDADES.minBy { abs(it - edicaoSalva.velocidadeLenta) })
    }
    var congelar by remember { mutableStateOf(edicaoSalva.congelarFinal) }
    var zoom by remember { mutableFloatStateOf(edicaoSalva.zoom.coerceIn(1f, ZOOM_MAXIMO)) }
    var centroX by remember { mutableFloatStateOf(edicaoSalva.centroX) }
    var centroY by remember { mutableFloatStateOf(edicaoSalva.centroY) }
    var espelhado by remember { mutableStateOf(edicaoSalva.espelhado) }
    // Linhas-guia por cima do vídeo, só para ajudar no enquadramento: não é salva.
    var grade by remember { mutableStateOf(false) }
    var alca by remember { mutableStateOf(Alca.INICIO) }
    var painel by remember { mutableStateOf(Painel.CORTE) }

    var posicao by remember { mutableIntStateOf(0) }
    var tocando by remember { mutableStateOf(true) }
    var semVideo by remember { mutableStateOf(false) }
    var miniaturas by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var proporcao by remember { mutableFloatStateOf(9f / 16f) }
    var tamanhoDaImagem by remember { mutableStateOf(IntSize.Zero) }
    // Fechar com mudanças sem salvar pede confirmação.
    var confirmarSaida by remember { mutableStateOf(false) }

    val tocador = remember(arquivo) { TocadorDoCorte(arquivo) }
    DisposableEffect(tocador) {
        onDispose { tocador.soltar() }
    }
    val erro = tocador.erro || semVideo

    /** Mantém o lento dentro do corte e com um tamanho mínimo. */
    fun encaixarLento() {
        if (fim - inicio < LENTO_MINIMO) return
        lentoInicio = limitar(lentoInicio, inicio, fim - LENTO_MINIMO)
        lentoFim = limitar(lentoFim, lentoInicio + LENTO_MINIMO, fim)
    }

    // O vídeo carregou: põe as alças onde a edição salva diz. Só uma vez, mesmo que o app
    // saia e volte, para não desfazer o que já foi mexido.
    LaunchedEffect(tocador.pronto) {
        if (!tocador.pronto || duracao > 0) return@LaunchedEffect
        val total = tocador.duracaoMs
        if (total <= 0) {
            semVideo = true
            return@LaunchedEffect
        }
        val t = total.toFloat()
        val fimInicial = (edicaoSalva.fimMs?.toFloat() ?: t).coerceIn(0f, t)
        fim = fimInicial
        inicio = (edicaoSalva.inicioMs?.toFloat() ?: 0f).coerceIn(0f, (fimInicial - TRECHO_MINIMO).coerceAtLeast(0f))
        // Sem lento salvo, o lento começa no terço do meio do corte, para facilitar.
        val terco = (fim - inicio) / 3f
        lentoInicio = edicaoSalva.lentoInicioMs?.toFloat() ?: (inicio + terco)
        lentoFim = edicaoSalva.lentoFimMs?.toFloat() ?: (fim - terco)
        encaixarLento()
        duracao = total
        posicao = inicio.toInt()
        tocador.irPara(inicio.toLong())
    }

    // As miniaturas da faixa e o formato do vídeo, lidos uma vez em segundo plano.
    LaunchedEffect(arquivo, duracao) {
        if (duracao <= 0) return@LaunchedEffect
        proporcao = withContext(Dispatchers.IO) { proporcaoDoVideo(arquivo) }
        miniaturas = withContext(Dispatchers.IO) { lerMiniaturas(arquivo, duracao.toLong()) }
    }

    // Toca o trecho em repetição, devagar dentro do lento, e acompanha a posição.
    LaunchedEffect(tocando, duracao, comLento) {
        if (duracao <= 0) return@LaunchedEffect
        if (!tocando) {
            tocador.pausar()
            return@LaunchedEffect
        }
        tocador.tocar()
        var velocidade = -1f
        while (true) {
            delay(30)
            // Enquanto o tocador vai para um ponto, a posição dele ainda é a antiga.
            if (tocador.buscando) continue
            val agora = tocador.posicao
            posicao = agora
            if (agora >= fim.toInt() || tocador.terminou) {
                if (congelar && !soCorte) {
                    // Como no vídeo final: o último quadro fica parado antes de recomeçar.
                    tocador.pausar()
                    delay(CONGELAMENTO_MS)
                }
                tocador.irPara(inicio.toLong())
                tocador.tocar()
                velocidade = -1f
                continue
            }
            val desejada = if (comLento && agora >= lentoInicio && agora < lentoFim) velocidadeLenta else 1f
            if (desejada != velocidade) {
                velocidade = desejada
                tocador.velocidade(desejada)
            }
        }
    }

    // App em segundo plano ou tela apagada: para de tocar (e de conferir a posição 30 vezes por segundo).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { tocando = false }

    /** Mostra parado o quadro de um ponto do vídeo. */
    fun mostrar(ms: Float) {
        tocando = false
        tocador.irPara(ms.toLong())
        posicao = ms.toInt()
    }

    /** Volta a tocar: de onde está, se estiver dentro do trecho; senão, do início dele. */
    fun tocarOuPausar() {
        if (!tocando && (posicao < inicio.toInt() || posicao >= fim.toInt() - 2 * UM_QUADRO_MS)) {
            tocador.irPara(inicio.toLong())
        }
        tocando = !tocando
    }

    fun moverInicio(ms: Float) {
        inicio = limitar(ms, 0f, fim - TRECHO_MINIMO)
        encaixarLento()
        alca = Alca.INICIO
        mostrar(inicio)
    }

    fun moverFim(ms: Float) {
        fim = limitar(ms, inicio + TRECHO_MINIMO, duracao.toFloat())
        encaixarLento()
        alca = Alca.FIM
        mostrar(fim)
    }

    /** Move a alça escolhida um quadro para trás (-1) ou para a frente (+1). */
    fun passo(sentido: Int) {
        val d = sentido * UM_QUADRO_MS.toFloat()
        when (alca) {
            Alca.INICIO -> moverInicio(inicio + d)
            Alca.FIM -> moverFim(fim + d)
            Alca.LENTO_INICIO -> {
                lentoInicio = limitar(lentoInicio + d, inicio, lentoFim - LENTO_MINIMO)
                mostrar(lentoInicio)
            }
            Alca.LENTO_FIM -> {
                lentoFim = limitar(lentoFim + d, lentoInicio + LENTO_MINIMO, fim)
                mostrar(lentoFim)
            }
        }
    }

    fun escolherAlca(nova: Alca) {
        alca = nova
        mostrar(
            when (nova) {
                Alca.INICIO -> inicio
                Alca.FIM -> fim
                Alca.LENTO_INICIO -> lentoInicio
                Alca.LENTO_FIM -> lentoFim
            },
        )
    }

    /** A edição como está na tela agora. */
    fun edicaoAtual(): EdicaoDoClipe {
        val total = duracao.toFloat()
        val inteiro = inicio <= FOLGA_DA_PONTA && fim >= total - FOLGA_DA_PONTA
        val comZoom = zoom > 1.01f
        return EdicaoDoClipe(
            inicioMs = if (inteiro) null else inicio.toLong(),
            fimMs = if (inteiro) null else fim.toLong(),
            lentoInicioMs = if (comLento) lentoInicio.toLong() else null,
            lentoFimMs = if (comLento) lentoFim.toLong() else null,
            congelarFinal = congelar,
            zoom = if (comZoom) zoom else 1f,
            centroX = if (comZoom) centroX else 0.5f,
            centroY = if (comZoom) centroY else 0.5f,
            velocidadeLenta = velocidadeLenta,
            espelhado = espelhado,
        ).ajustada(duracao.toLong())
    }

    /** Verdadeiro se algo foi mexido desde que a tela abriu. */
    fun mudou(): Boolean {
        if (duracao <= 0) return false
        val d = duracao.toLong()
        // "Sem corte" e "corte do zero ao fim" são a mesma edição.
        fun EdicaoDoClipe.comparavel(): EdicaoDoClipe {
            val a = ajustada(d)
            val inteiro = (a.inicioMs ?: 0L) <= FOLGA_DA_PONTA && (a.fimMs ?: d) >= d - FOLGA_DA_PONTA
            return a.copy(
                inicioMs = if (inteiro) 0L else a.inicioMs ?: 0L,
                fimMs = if (inteiro) d else a.fimMs ?: d,
                velocidadeLenta = if (a.temLento) a.velocidadeLenta else 0.5f,
                centroX = if (a.temZoom) a.centroX else 0.5f,
                centroY = if (a.temZoom) a.centroY else 0.5f,
            )
        }
        val salva = edicaoSalva.copy(velocidadeLenta = VELOCIDADES.minBy { abs(it - edicaoSalva.velocidadeLenta) })
        return edicaoAtual().comparavel() != salva.comparavel()
    }

    fun fechar() {
        if (!confirmarSaida && mudou()) confirmarSaida = true else onFechar()
    }

    BackHandler { fechar() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ----- topo -----
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotaoQuadrado(R.drawable.ic_fechar, "Fechar sem salvar", { fechar() }, fundo = Cores.Superficie)
            Text(
                text = titulo.uppercase(),
                style = Tipos.TituloDeSecao,
                color = Cores.Texto,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (!soCorte) BotaoQuadrado(
                icone = R.drawable.ic_grade,
                descricao = if (grade) "Esconder a grade de enquadramento" else "Mostrar a grade de enquadramento",
                onClick = { grade = !grade },
                fundo = if (grade) Cores.Destaque else Cores.Superficie,
                cor = if (grade) Cores.Fundo else Cores.Texto,
                tamanho = 40.dp,
            )
            if (onRegravar != null) BotaoContorno("Regravar", onRegravar, altura = 40.dp, fundo = Cores.Superficie)
        }

        // ----- vídeo: aparece como vai sair (zoom e espelho), com a grade por cima -----
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(proporcao)
                    .clipToBounds()
                    .onSizeChanged { tamanhoDaImagem = it },
            ) {
                val z = if (soCorte) 1f else zoom
                key(tocador) {
                    ImagemDoCorte(
                        tocador = tocador,
                        escalaX = if (espelhado && !soCorte) -z else z,
                        escalaY = z,
                        // O centro escolhido vai para o meio do quadro.
                        deslocamentoX = -z * (limitarCentro(centroX, z) - 0.5f) * tamanhoDaImagem.width,
                        deslocamentoY = -z * (limitarCentro(centroY, z) - 0.5f) * tamanhoDaImagem.height,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(soCorte) {
                            // Tocar na imagem pausa e continua.
                            detectTapGestures { if (duracao > 0) tocarOuPausar() }
                        }
                        .pointerInput(soCorte) {
                            if (soCorte) return@pointerInput
                            // Dois dedos aproximam; arrastar escolhe o que aparece.
                            detectTransformGestures { _, arrasto, aproximacao, _ ->
                                val novo = (zoom * aproximacao).coerceIn(1f, ZOOM_MAXIMO)
                                zoom = novo
                                val largura = size.width.coerceAtLeast(1)
                                val altura = size.height.coerceAtLeast(1)
                                centroX = limitarCentro(centroX - arrasto.x / (novo * largura), novo)
                                centroY = limitarCentro(centroY - arrasto.y / (novo * altura), novo)
                            }
                        }
                        .drawBehind {
                            if (!grade) return@drawBehind
                            val linha = Color.White.copy(alpha = 0.45f)
                            for (f in listOf(1f / 3f, 2f / 3f)) {
                                drawLine(linha, Offset(size.width * f, 0f), Offset(size.width * f, size.height), 1.dp.toPx())
                                drawLine(linha, Offset(0f, size.height * f), Offset(size.width, size.height * f), 1.dp.toPx())
                            }
                        },
                )
            }
            when {
                erro -> Text("Não consegui abrir este vídeo.", style = Tipos.Corpo, color = Color.White)
                duracao <= 0 -> CircularProgressIndicator(color = Cores.Destaque)
                else -> Unit
            }
        }

        if (duracao > 0) {
            val total = duracao.toFloat()

            // ----- tempos -----
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Início ${tempoPreciso(inicio.toLong())}", style = Tipos.CorpoPequeno, color = Cores.Texto)
                val trecho = (fim - inicio).toLong()
                val noFinal = if (soCorte) trecho else duracaoDoClipeNaSaidaMs(edicaoAtual(), duracao.toLong())
                Text(
                    // Com lento ou congelamento, o trecho dura mais no vídeo montado.
                    text = if (abs(noFinal - trecho) >= 100) {
                        "${duracaoCurta(trecho)} → ${duracaoCurta(noFinal)} no vídeo"
                    } else {
                        "Trecho de ${duracaoCurta(trecho)} · agora ${tempoPreciso(posicao.toLong())}"
                    },
                    style = Tipos.CorpoPequeno,
                    color = Cores.TextoSuave,
                    maxLines = 1,
                )
                Text("Fim ${tempoPreciso(fim.toLong())}", style = Tipos.CorpoPequeno, color = Cores.Texto)
            }

            // ----- a faixa do vídeo: alças do corte, o lento em laranja e a posição -----
            LinhaDoTempo(
                miniaturas = miniaturas,
                total = total,
                inicio = inicio,
                fim = fim,
                lento = if (comLento && !soCorte) lentoInicio..lentoFim else null,
                posicao = posicao.toFloat(),
                alcaEmUso = alca,
                aoMoverInicio = { moverInicio(it) },
                aoMoverFim = { moverFim(it) },
                aoProcurar = { mostrar(it.coerceIn(0f, total)) },
                aoSoltarAlca = {
                    // Soltou a alça: toca o trecho novo para conferir.
                    tocador.irPara(inicio.toLong())
                    posicao = inicio.toInt()
                    tocando = true
                },
            )

            // ----- quadro a quadro e tocar -----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotaoContorno("◂ 1 quadro", { passo(-1) }, Modifier.weight(1f), altura = 40.dp, fundo = Cores.Superficie)
                BotaoContorno(
                    texto = if (tocando) "Pausar" else "Tocar",
                    onClick = { tocarOuPausar() },
                    modifier = Modifier.weight(1f),
                    altura = 40.dp,
                    fundo = Cores.Superficie,
                )
                BotaoContorno("1 quadro ▸", { passo(1) }, Modifier.weight(1f), altura = 40.dp, fundo = Cores.Superficie)
            }

            // Na aba Câmera o vídeo só é cortado: os efeitos ficam para os vídeos das séries.
            if (!soCorte) {
                Segmentos(
                    opcoes = Painel.entries.map { p ->
                        // Um ponto marca a ferramenta que está em uso neste clipe.
                        val emUso = when (p) {
                            Painel.CORTE -> false
                            Painel.LENTO -> comLento
                            Painel.ZOOM -> zoom > 1.01f
                            Painel.MAIS -> espelhado || congelar
                        }
                        if (emUso) "${p.rotulo} •" else p.rotulo
                    },
                    selecionada = painel.ordinal,
                    onSelecionar = { i ->
                        painel = Painel.entries[i]
                        // O quadro a quadro passa a mexer no que o painel mostra.
                        if (painel == Painel.LENTO && comLento && alca.ordinal < 2) alca = Alca.LENTO_INICIO
                        if (painel == Painel.CORTE && alca.ordinal >= 2) alca = Alca.INICIO
                    },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 136.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (if (soCorte) Painel.CORTE else painel) {
                    Painel.CORTE -> {
                        Text(
                            text = "Arraste as alças laranja na faixa, ou escolha uma ponta e ajuste quadro a quadro.",
                            style = Tipos.CorpoPequeno,
                            color = Cores.TextoSuave,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Segmentos(
                                opcoes = listOf(Alca.INICIO.rotulo, Alca.FIM.rotulo),
                                selecionada = alca.ordinal.takeIf { it < 2 },
                                onSelecionar = { escolherAlca(Alca.entries[it]) },
                                modifier = Modifier.weight(1.4f),
                            )
                            BotaoContorno(
                                texto = "Vídeo inteiro",
                                onClick = {
                                    inicio = 0f
                                    fim = total
                                    encaixarLento()
                                    tocador.irPara(0L)
                                    posicao = 0
                                    tocando = true
                                },
                                modifier = Modifier.weight(1f),
                                fundo = Cores.Superficie,
                            )
                        }
                    }

                    Painel.LENTO -> {
                        LinhaDeEdicao(
                            texto = "Câmera lenta (${rotuloDaVelocidade(velocidadeLenta)})",
                            detalhe = if (comLento) {
                                "${tempoPreciso(lentoInicio.toLong())} a ${tempoPreciso(lentoFim.toLong())}, a faixa laranja"
                            } else {
                                "Deixa a repetição mais devagar no vídeo final"
                            },
                            ligado = comLento,
                            onMudar = { ligar ->
                                if (ligar && fim - inicio >= LENTO_MINIMO) {
                                    encaixarLento()
                                    comLento = true
                                    alca = Alca.LENTO_INICIO
                                } else {
                                    comLento = false
                                    if (alca.ordinal >= 2) alca = Alca.INICIO
                                }
                            },
                        )
                        if (comLento) {
                            RangeSlider(
                                value = lentoInicio..lentoFim,
                                onValueChange = { faixa ->
                                    val mexeuNoInicio = faixa.start != lentoInicio
                                    var a = faixa.start.coerceIn(inicio, fim)
                                    var b = faixa.endInclusive.coerceIn(inicio, fim)
                                    if (b - a < LENTO_MINIMO) {
                                        if (mexeuNoInicio) a = (b - LENTO_MINIMO).coerceAtLeast(inicio) else b = (a + LENTO_MINIMO).coerceAtMost(fim)
                                    }
                                    lentoInicio = a
                                    lentoFim = b
                                    alca = if (mexeuNoInicio) Alca.LENTO_INICIO else Alca.LENTO_FIM
                                    mostrar(if (mexeuNoInicio) a else b)
                                },
                                onValueChangeFinished = {
                                    // Toca um pouco antes do lento, para ver a entrada nele.
                                    val antes = (lentoInicio - 500f).coerceAtLeast(inicio)
                                    tocador.irPara(antes.toLong())
                                    posicao = antes.toInt()
                                    tocando = true
                                },
                                valueRange = inicio..fim,
                                colors = coresDoSlider(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Segmentos(
                                    opcoes = VELOCIDADES.map { rotuloDaVelocidade(it) },
                                    selecionada = VELOCIDADES.indexOf(velocidadeLenta).takeIf { it >= 0 },
                                    onSelecionar = { velocidadeLenta = VELOCIDADES[it] },
                                    modifier = Modifier.weight(1f),
                                )
                                Segmentos(
                                    opcoes = listOf(Alca.LENTO_INICIO.rotulo, Alca.LENTO_FIM.rotulo),
                                    selecionada = (alca.ordinal - 2).takeIf { it >= 0 },
                                    onSelecionar = { escolherAlca(Alca.entries[it + 2]) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    Painel.ZOOM -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Slider(
                                value = zoom,
                                onValueChange = {
                                    zoom = it
                                    centroX = limitarCentro(centroX, it)
                                    centroY = limitarCentro(centroY, it)
                                },
                                valueRange = 1f..ZOOM_MAXIMO,
                                colors = coresDoSlider(),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "%.1fx".format(zoom).replace('.', ','),
                                style = Tipos.CorpoForte,
                                color = Cores.Texto,
                            )
                            BotaoContorno(
                                texto = "Tirar",
                                onClick = {
                                    zoom = 1f
                                    centroX = 0.5f
                                    centroY = 0.5f
                                },
                                ativo = zoom > 1.01f,
                                altura = 40.dp,
                                fundo = Cores.Superficie,
                            )
                        }
                        Text(
                            text = if (zoom > 1.01f) {
                                "O vídeo sai como está aparecendo. Arraste a imagem para escolher o que fica."
                            } else {
                                "Use o controle ou abra dois dedos na imagem para aproximar."
                            },
                            style = Tipos.CorpoPequeno,
                            color = Cores.TextoSuave,
                        )
                    }

                    Painel.MAIS -> {
                        LinhaDeEdicao(
                            texto = "Espelhar",
                            detalhe = "Vira o vídeo na horizontal, do início ao fim",
                            ligado = espelhado,
                            onMudar = { ligar ->
                                espelhado = ligar
                                // A imagem virou: o enquadramento do zoom continua no mesmo lugar dela.
                                centroX = 1f - centroX
                            },
                        )
                        LinhaDeEdicao(
                            texto = "Congelar o último quadro (1 s)",
                            detalhe = "Segura o final da repetição antes do próximo clipe",
                            ligado = congelar,
                            onMudar = { congelar = it },
                        )
                    }
                }
            }

            // ----- salvar: sempre à vista -----
            if (confirmarSaida) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sair sem salvar?",
                        style = Tipos.CorpoForte,
                        color = Cores.Texto,
                        modifier = Modifier.weight(1f),
                    )
                    BotaoContorno("Descartar", onFechar, altura = 48.dp, fundo = Cores.Superficie)
                    BotaoPrincipal("Continuar", { confirmarSaida = false })
                }
            } else {
                BotaoPrincipal(
                    texto = if (soCorte) "Cortar o vídeo" else "Salvar edição",
                    onClick = { onSalvar(edicaoAtual()) },
                    modifier = Modifier.fillMaxWidth(),
                    icone = R.drawable.ic_feito,
                )
            }
        } else if (erro) {
            BotaoContorno("Fechar", onFechar, Modifier.fillMaxWidth(), altura = 48.dp, fundo = Cores.Superficie)
        }
    }
}

/**
 * A imagem do tocador, já com o zoom e o espelho da edição. Quem chama recorta o que
 * passa do quadro.
 */
@Composable
private fun ImagemDoCorte(
    tocador: TocadorDoCorte,
    escalaX: Float,
    escalaY: Float,
    deslocamentoX: Float,
    deslocamentoY: Float,
    modifier: Modifier,
) {
    AndroidView(
        factory = { contexto ->
            TextureView(contexto).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var superficie: Surface? = null

                    override fun onSurfaceTextureAvailable(textura: SurfaceTexture, largura: Int, altura: Int) {
                        val nova = Surface(textura)
                        superficie = nova
                        tocador.ligarTela(nova)
                    }

                    override fun onSurfaceTextureSizeChanged(textura: SurfaceTexture, largura: Int, altura: Int) = Unit

                    override fun onSurfaceTextureDestroyed(textura: SurfaceTexture): Boolean {
                        tocador.ligarTela(null)
                        superficie?.release()
                        superficie = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(textura: SurfaceTexture) = Unit
                }
            }
        },
        update = { tela ->
            tela.scaleX = escalaX
            tela.scaleY = escalaY
            tela.translationX = deslocamentoX
            tela.translationY = deslocamentoY
        },
        modifier = modifier,
    )
}

/**
 * A faixa de miniaturas com o corte. Arrastar perto de uma alça laranja move o início ou o
 * fim; arrastar ou tocar em outro lugar mostra aquele ponto do vídeo. Tempos em milissegundos.
 *
 * @param lento o trecho em câmera lenta, desenhado em laranja; nulo = sem lento
 * @param alcaEmUso a alça que o "quadro a quadro" está mexendo (fica com o risco branco)
 */
@Composable
private fun LinhaDoTempo(
    miniaturas: List<ImageBitmap>,
    total: Float,
    inicio: Float,
    fim: Float,
    lento: ClosedFloatingPointRange<Float>?,
    posicao: Float,
    alcaEmUso: Alca,
    aoMoverInicio: (Float) -> Unit,
    aoMoverFim: (Float) -> Unit,
    aoProcurar: (Float) -> Unit,
    aoSoltarAlca: () -> Unit,
) {
    // O gesto dura mais que uma recomposição: lê sempre os valores de agora.
    val pontas by rememberUpdatedState(Triple(inicio, fim, total))
    val moverInicio by rememberUpdatedState(aoMoverInicio)
    val moverFim by rememberUpdatedState(aoMoverFim)
    val procurar by rememberUpdatedState(aoProcurar)
    val soltarAlca by rememberUpdatedState(aoSoltarAlca)
    val canto = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .pointerInput(Unit) {
                detectTapGestures { toque ->
                    procurar((toque.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) * pontas.third)
                }
            }
            .pointerInput(Unit) {
                // 0 = só mostrar o ponto, 1 = alça do início, 2 = alça do fim
                var alvo = 0
                detectDragGestures(
                    onDragStart = { toque ->
                        val (i, f, t) = pontas
                        val xDoInicio = i / t * size.width
                        val xDoFim = f / t * size.width
                        val alcance = 28.dp.toPx()
                        val ateOInicio = abs(toque.x - xDoInicio)
                        val ateOFim = abs(toque.x - xDoFim)
                        alvo = when {
                            // Alças encostadas: à esquerda das duas pega o início, à direita o fim.
                            ateOInicio <= alcance && (ateOInicio < ateOFim || toque.x < xDoInicio) -> 1
                            ateOFim <= alcance -> 2
                            else -> 0
                        }
                    },
                    onDragEnd = { if (alvo != 0) soltarAlca() },
                    onDragCancel = { if (alvo != 0) soltarAlca() },
                    onDrag = { mudanca, _ ->
                        mudanca.consume()
                        val ms = (mudanca.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) * pontas.third
                        when (alvo) {
                            1 -> moverInicio(ms)
                            2 -> moverFim(ms)
                            else -> procurar(ms)
                        }
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
                .clip(canto)
                .background(Cores.Superficie),
        ) {
            for (m in miniaturas) {
                Image(
                    bitmap = m,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    if (total <= 0f) return@drawBehind
                    val topo = 4.dp.toPx()
                    val altura = size.height - 2 * topo
                    val xDoInicio = size.width * (inicio / total).coerceIn(0f, 1f)
                    val xDoFim = size.width * (fim / total).coerceIn(0f, 1f)

                    // O que fica de fora do corte, escurecido.
                    val sombra = Color.Black.copy(alpha = 0.65f)
                    drawRect(sombra, Offset(0f, topo), Size(xDoInicio, altura))
                    drawRect(sombra, Offset(xDoFim, topo), Size(size.width - xDoFim, altura))

                    if (lento != null) {
                        val x = size.width * (lento.start / total).coerceIn(0f, 1f)
                        val l = size.width * ((lento.endInclusive - lento.start) / total).coerceIn(0f, 1f)
                        drawRect(Cores.Destaque.copy(alpha = 0.3f), Offset(x, topo), Size(l, altura))
                        drawRect(Cores.Destaque, Offset(x, topo + altura - 4.dp.toPx()), Size(l, 4.dp.toPx()))
                    }

                    // A moldura do trecho e as duas alças.
                    val risco = 2.dp.toPx()
                    drawRect(
                        color = Cores.Destaque,
                        topLeft = Offset(xDoInicio, topo + risco / 2),
                        size = Size((xDoFim - xDoInicio).coerceAtLeast(0f), altura - risco),
                        style = Stroke(risco),
                    )
                    val largura = 12.dp.toPx()
                    val raio = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    fun alca(x: Float, emUso: Boolean) {
                        val centro = x.coerceIn(largura / 2, size.width - largura / 2)
                        drawRoundRect(Cores.Destaque, Offset(centro - largura / 2, 0f), Size(largura, size.height), raio)
                        drawLine(
                            color = if (emUso) Color.White else Cores.Fundo,
                            start = Offset(centro, size.height * 0.3f),
                            end = Offset(centro, size.height * 0.7f),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                    alca(xDoInicio, alcaEmUso == Alca.INICIO)
                    alca(xDoFim, alcaEmUso == Alca.FIM)

                    // Onde o vídeo está agora.
                    val p = size.width * (posicao / total).coerceIn(0f, 1f)
                    drawRect(Color.White, Offset(p - 1.dp.toPx(), topo), Size(2.dp.toPx(), altura))
                },
        )
    }
}

/** Uma chave de edição: o nome, uma linha de explicação e o liga/desliga. */
@Composable
private fun LinhaDeEdicao(texto: String, detalhe: String, ligado: Boolean, onMudar: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Cores.Superficie)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = texto, style = Tipos.CorpoForte, color = Cores.Texto)
            Text(text = detalhe, style = Tipos.CorpoPequeno, color = Cores.TextoSuave)
        }
        Switch(
            checked = ligado,
            onCheckedChange = onMudar,
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

@Composable
private fun coresDoSlider() = SliderDefaults.colors(
    thumbColor = Cores.Destaque,
    activeTrackColor = Cores.Destaque,
    inactiveTrackColor = Cores.BordaForte,
)

/** As miniaturas da faixa, pequenas para não pesar na memória. */
private fun lerMiniaturas(arquivo: File, duracaoMs: Long): List<ImageBitmap> {
    val leitor = MediaMetadataRetriever()
    return try {
        leitor.setDataSource(arquivo.absolutePath)
        temposDasMiniaturas(duracaoMs, MINIATURAS).mapNotNull { ms ->
            val quadro = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    // Já vem pequeno: não chega a existir um quadro de 1080p (8 MB) na memória.
                    leitor.getScaledFrameAtTime(
                        ms * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        LADO_DA_MINIATURA,
                        LADO_DA_MINIATURA,
                    )
                } else {
                    leitor.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            }.getOrNull() ?: return@mapNotNull null
            val maior = maxOf(quadro.width, quadro.height, 1)
            if (maior <= LADO_DA_MINIATURA) return@mapNotNull quadro.asImageBitmap()
            val largura = (quadro.width * LADO_DA_MINIATURA / maior).coerceAtLeast(1)
            val altura = (quadro.height * LADO_DA_MINIATURA / maior).coerceAtLeast(1)
            val pequena = Bitmap.createScaledBitmap(quadro, largura, altura, true)
            if (pequena !== quadro) quadro.recycle()
            pequena.asImageBitmap()
        }
    } catch (e: Exception) {
        emptyList()
    } finally {
        runCatching { leitor.release() }
    }
}
