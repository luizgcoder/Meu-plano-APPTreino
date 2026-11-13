package com.luiz.meuplano.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.luiz.meuplano.R
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Um clipe do vídeo final.
 *
 * @param edicao corte, câmera lenta, congelar o final e zoom
 * @param titulo primeira linha da legenda (o exercício)
 * @param detalhe segunda linha (séries, carga e RPE)
 */
data class Clipe(
    val arquivo: File,
    val edicao: EdicaoDoClipe,
    val titulo: String,
    val detalhe: String,
)

/** O clipe com o que precisa ser lido do arquivo antes de montar. */
private class ClipePreparado(
    val clipe: Clipe,
    val duracaoMs: Long,
    /** O último quadro do corte, salvo como imagem, para o congelamento. Nulo se não pediu ou não deu. */
    val quadroFinal: File?,
)

/**
 * Junta os clipes de um treino em um vídeo só, cada um com sua legenda no canto
 * inferior esquerdo, e aplica a edição: câmera lenta, congelar o final, zoom, fade
 * entre os clipes, legenda entrando e o som escolhido.
 *
 * Usa o Media3 Transformer, que corta, junta e desenha por cima usando os codecs
 * do próprio aparelho. O vídeo sai em pé, 1080x1920.
 */
@OptIn(UnstableApi::class)
class MontadorDeVideo(private val contexto: Context) {

    /**
     * Se o aparelho não conseguir com todos os efeitos, tenta de novo sem os mais pesados
     * (câmera lenta, congelamento, fade e legenda animada).
     *
     * @param aoProgredir recebe de 0 a 100 enquanto o vídeo é montado
     * @return verdadeiro se precisou montar na versão simplificada
     * @throws ExportException se nem a versão simplificada funcionar
     */
    suspend fun montar(
        clipes: List<Clipe>,
        saida: File,
        opcoes: OpcoesDaMontagem,
        aoProgredir: (Int) -> Unit,
    ): Boolean {
        require(clipes.isNotEmpty()) { "Sem clipes para montar" }
        // A pasta do feedback da semana pode ainda não existir (plano novo): sem ela,
        // o Transformer não consegue criar o arquivo e dá "Muxer error".
        withContext(Dispatchers.IO) { saida.parentFile?.mkdirs() }
        val pasta = File(contexto.cacheDir, "montagem").apply { mkdirs() }
        try {
            val preparados = withContext(Dispatchers.IO) { clipes.map { preparar(it, pasta) } }
            return try {
                withContext(Dispatchers.Main) { exportar(composicao(preparados, opcoes, simples = false), saida, aoProgredir) }
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aoProgredir(0)
                withContext(Dispatchers.Main) {
                    withContext(Dispatchers.IO) { saida.delete() }
                    exportar(composicao(preparados, opcoes.simplificada(), simples = true), saida, aoProgredir)
                }
                true
            }
        } finally {
            // NonCancellable: com a montagem cancelada, um withContext comum nem chegaria a rodar
            // e os quadros temporários ficariam para sempre no cache.
            withContext(NonCancellable + Dispatchers.IO) { pasta.deleteRecursively() }
        }
    }

    /** Lê a duração do vídeo e, se o clipe congela o final, guarda o último quadro do corte. */
    private fun preparar(clipe: Clipe, pasta: File): ClipePreparado {
        val leitor = MediaMetadataRetriever()
        try {
            leitor.setDataSource(clipe.arquivo.absolutePath)
            val duracao = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val edicao = clipe.edicao.ajustada(duracao)
            var quadro: File? = null
            if (edicao.congelarFinal && duracao > 0) {
                // Um pouco antes do fim, para pegar um quadro que existe de verdade.
                val fim = (edicao.fimMs ?: duracao)
                val momento = (fim - 2 * UM_QUADRO_MS).coerceAtLeast(edicao.inicioMs ?: 0L)
                val imagem = runCatching {
                    leitor.getFrameAtTime(momento * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                }.getOrNull()
                if (imagem != null) {
                    val emPe = endireitar(imagem, leitor)
                    try {
                        val arquivo = File(pasta, "quadro_${System.nanoTime()}.jpg")
                        arquivo.outputStream().use { emPe.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                        quadro = arquivo
                    } finally {
                        // Um quadro de 1080p ocupa uns 8 MB: devolve já, sem esperar o coletor de lixo.
                        if (emPe !== imagem) emPe.recycle()
                        imagem.recycle()
                    }
                }
            }
            return ClipePreparado(clipe, duracao, quadro)
        } catch (e: Exception) {
            // Sem conseguir ler, monta sem congelar; a duração 0 deixa o corte como foi salvo.
            return ClipePreparado(clipe, 0L, null)
        } finally {
            runCatching { leitor.release() }
        }
    }

    /**
     * Garante o quadro em pé. Normalmente o Android já gira, mas se ele vier com o tamanho
     * do vídeo sem girar, gira aqui pelo ângulo gravado no arquivo.
     */
    private fun endireitar(quadro: Bitmap, leitor: MediaMetadataRetriever): Bitmap {
        val rotacao = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val largura = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val altura = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val deitado = rotacao % 180 != 0
        val veioSemGirar = deitado && largura != altura && quadro.width == largura && quadro.height == altura
        if (!veioSemGirar) return quadro
        val giro = Matrix().apply { postRotate(rotacao.toFloat()) }
        return Bitmap.createBitmap(quadro, 0, 0, quadro.width, quadro.height, giro, true)
    }

    /** Monta a composição: uma sequência de vídeo e, com música, uma de áudio em repetição. */
    private fun composicao(clipes: List<ClipePreparado>, opcoes: OpcoesDaMontagem, simples: Boolean): Composition {
        val som = opcoes.somEfetivo
        val itens = mutableListOf<EditedMediaItem>()
        for (preparado in clipes) {
            val original = if (preparado.duracaoMs > 0) preparado.clipe.edicao.ajustada(preparado.duracaoMs) else preparado.clipe.edicao
            val edicao = if (simples) original.copy(lentoInicioMs = null, lentoFimMs = null, congelarFinal = false) else original
            val quadroFinal = preparado.quadroFinal.takeIf { edicao.congelarFinal }
            val legenda = desenharLegenda(preparado.clipe.titulo, preparado.clipe.detalhe)
            for (pedaco in pedacosParaMontar(edicao, preparado.duracaoMs)) {
                itens += trechoDeVideo(
                    preparado = preparado,
                    edicao = edicao,
                    pedaco = pedaco,
                    legenda = legenda,
                    fadeEntrada = opcoes.transicoes && pedaco.primeiro,
                    fadeSaida = opcoes.transicoes && pedaco.ultimo && quadroFinal == null,
                    animarLegenda = opcoes.legendaAnimada && pedaco.primeiro,
                    som = som,
                )
            }
            if (quadroFinal != null) itens += quadroParado(quadroFinal, edicao, legenda, fadeSaida = opcoes.transicoes)
        }

        val video = EditedMediaItemSequence.Builder(itens)
            // Clipe gravado sem som no meio de outros com som: o Transformer completa com silêncio.
            .experimentalSetForceAudioTrack(som == SomDoVideo.ORIGINAL)
            .build()
        val musica = opcoes.musica
        // Vídeo da câmera do celular pode vir em HDR: converte para o normal, para todos saírem iguais.
        if (som != SomDoVideo.MUSICA || musica == null) {
            return Composition.Builder(video).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
        }
        val trilha = EditedMediaItemSequence.Builder(
            EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(musica))).setRemoveVideo(true).build(),
        )
            // A música repete enquanto houver vídeo, e para junto com ele.
            .setIsLooping(true)
            .build()
        return Composition.Builder(video, trilha).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
    }

    /**
     * Os pedaços do clipe. Sem a duração do arquivo (não deu para ler), vai um pedaço só,
     * com o corte como foi salvo.
     */
    private fun pedacosParaMontar(edicao: EdicaoDoClipe, duracaoMs: Long): List<Pedaco> =
        if (duracaoMs > 0) {
            pedacosDoClipe(edicao, duracaoMs)
        } else {
            listOf(Pedaco(edicao.inicioMs ?: 0L, edicao.fimMs, 1f, primeiro = true, ultimo = true))
        }

    /** Um pedaço de clipe: corte, velocidade, zoom, tamanho do quadro, legenda e fade. */
    private fun trechoDeVideo(
        preparado: ClipePreparado,
        edicao: EdicaoDoClipe,
        pedaco: Pedaco,
        legenda: Bitmap,
        fadeEntrada: Boolean,
        fadeSaida: Boolean,
        animarLegenda: Boolean,
        som: SomDoVideo,
    ): EditedMediaItem {
        val corte = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(pedaco.inicioMs)
        pedaco.fimMs?.let { corte.setEndPositionMs(it) }
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(preparado.clipe.arquivo))
            .setClippingConfiguration(corte.build())
            .build()

        val comSom = som == SomDoVideo.ORIGINAL
        val audio = mutableListOf<AudioProcessor>()
        val efeitos = mutableListOf<Effect>()
        // A velocidade vem primeiro: os efeitos seguintes já veem o tempo do vídeo final.
        if (pedaco.lento) {
            if (comSom) {
                val par = Effects.createExperimentalSpeedChangingEffect(VelocidadeFixa(pedaco.velocidade))
                audio += par.first
                efeitos += par.second
            } else {
                efeitos += SpeedChangeEffect(pedaco.velocidade)
            }
        }
        efeitos += efeitosDoQuadro(edicao, legenda, animarLegenda)
        // Sem saber onde o pedaço termina, o fade de saída fica de fora.
        val sabeOFim = pedaco.fimMs != null || preparado.duracaoMs > 0
        val saida = fadeSaida && sabeOFim
        if (fadeEntrada || saida) {
            val duracaoUs = pedaco.duracaoNaSaidaMs(preparado.duracaoMs) * 1000
            efeitos += Fade(duracaoUs, fadeEntrada, saida)
        }
        return EditedMediaItem.Builder(item)
            .setRemoveAudio(!comSom)
            .setEffects(Effects(audio, efeitos))
            .build()
    }

    /** O último quadro parado por um segundo, com o mesmo zoom e a mesma legenda do clipe. */
    private fun quadroParado(imagem: File, edicao: EdicaoDoClipe, legenda: Bitmap, fadeSaida: Boolean): EditedMediaItem {
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(imagem))
            .setMimeType(MimeTypes.IMAGE_JPEG)
            .setImageDurationMs(CONGELAMENTO_MS)
            .build()
        val efeitos = efeitosDoQuadro(edicao, legenda, animar = false).toMutableList<Effect>()
        if (fadeSaida) efeitos += Fade(CONGELAMENTO_MS * 1000, entrada = false, saida = true)
        return EditedMediaItem.Builder(item)
            .setFrameRate(30)
            .setEffects(Effects(emptyList(), efeitos))
            .build()
    }

    /** Zoom, quadro em pé de 1080x1920 e a legenda: o que todo pedaço tem. */
    private fun efeitosDoQuadro(edicao: EdicaoDoClipe, legenda: Bitmap, animar: Boolean): List<Effect> {
        val efeitos = mutableListOf<Effect>()
        // Primeiro espelha, depois recorta: na tela de corte o enquadramento do zoom é escolhido
        // olhando a imagem já espelhada, então o recorte tem que valer para ela.
        if (edicao.espelhado) {
            efeitos += ScaleAndRotateTransformation.Builder().setScale(-1f, 1f).build()
        }
        recorteDoZoom(edicao.zoom, edicao.centroX, edicao.centroY)?.let { (esquerda, direita, embaixo, emCima) ->
            efeitos += Crop(esquerda, direita, embaixo, emCima)
        }
        // Todos os clipes no mesmo quadro, com faixas pretas se o formato for outro.
        efeitos += Presentation.createForWidthAndHeight(LARGURA, ALTURA, Presentation.LAYOUT_SCALE_TO_FIT)
        val sobreposicao: TextureOverlay = if (animar) {
            LegendaEntrando(legenda)
        } else {
            BitmapOverlay.createStaticBitmapOverlay(legenda, posicaoDaLegenda(1f))
        }
        efeitos += OverlayEffect(listOf(sobreposicao))
        return efeitos
    }

    /**
     * Um clipe sozinho, só com o corte aplicado: sem legenda, no tamanho e com o som em que
     * foi gravado. É o que vai para a galeria quando você baixa um vídeo específico.
     *
     * @param inicioMs corte salvo; os dois nulos = vídeo inteiro
     */
    suspend fun recortar(arquivo: File, inicioMs: Long?, fimMs: Long?, saida: File) {
        withContext(Dispatchers.IO) {
            saida.parentFile?.mkdirs()
            saida.delete()
        }
        withContext(Dispatchers.Main) {
            val item = MediaItem.Builder().setUri(Uri.fromFile(arquivo))
            if (inicioMs != null || fimMs != null) {
                val corte = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(inicioMs ?: 0L)
                fimMs?.let { corte.setEndPositionMs(it) }
                item.setClippingConfiguration(corte.build())
            }
            val trecho = EditedMediaItem.Builder(item.build()).build()
            val composicao = Composition.Builder(EditedMediaItemSequence.Builder(listOf(trecho)).build()).build()
            exportar(composicao, saida) { }
        }
    }

    /** Roda o Transformer e espera terminar. Tem que ser chamado na thread principal. */
    private suspend fun exportar(composicao: Composition, saida: File, aoProgredir: (Int) -> Unit) {
        coroutineScope {
            var emAndamento: Transformer? = null
            val acompanhamento = launch {
                val progresso = ProgressHolder()
                while (isActive) {
                    delay(300)
                    val atual = emAndamento ?: continue
                    if (atual.getProgress(progresso) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        aoProgredir(progresso.progress)
                    }
                }
            }
            try {
                suspendCancellableCoroutine<Unit> { continuacao ->
                    val transformer = Transformer.Builder(contexto)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .addListener(
                            object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    if (continuacao.isActive) continuacao.resume(Unit)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException,
                                ) {
                                    if (continuacao.isActive) continuacao.resumeWithException(exportException)
                                }
                            },
                        )
                        .build()
                    emAndamento = transformer
                    // O cancelamento pode chegar de outra thread; o Transformer exige a principal.
                    continuacao.invokeOnCancellation {
                        Handler(Looper.getMainLooper()).post { transformer.cancel() }
                    }
                    transformer.start(composicao, saida.absolutePath)
                }
                aoProgredir(100)
            } finally {
                acompanhamento.cancel()
            }
        }
    }

    /**
     * A legenda como imagem transparente: o nome do exercício em Barlow Condensed e,
     * embaixo, séries, carga e RPE. Sem fundo; uma sombra no texto garante a leitura
     * por cima de qualquer cena. O texto quebra de linha se não couber.
     */
    private fun desenharLegenda(titulo: String, detalhe: String): Bitmap {
        // As fontes do app; se alguma não carregar, cai no negrito padrão do aparelho.
        val fonteDoTitulo = runCatching { ResourcesCompat.getFont(contexto, R.font.barlow_condensed_bold) }.getOrNull()
            ?: Typeface.DEFAULT_BOLD
        val fonteDoDetalhe = runCatching { ResourcesCompat.getFont(contexto, R.font.barlow_semibold) }.getOrNull()
            ?: Typeface.DEFAULT_BOLD

        fun tinta(fonte: Typeface, tamanho: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = fonte
            textSize = tamanho
            setShadowLayer(SOMBRA, 0f, 2f, Color.argb(220, 0, 0, 0))
        }

        val larguraMaxima = LARGURA - 2 * (LARGURA * MARGEM_NO_QUADRO / 2).toInt() - 2 * RESPIRO
        fun bloco(texto: String, tinta: TextPaint) =
            StaticLayout.Builder.obtain(texto, 0, texto.length, tinta, larguraMaxima)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

        val blocos = buildList {
            add(bloco(titulo.uppercase(), tinta(fonteDoTitulo, TAMANHO_DO_TITULO)))
            if (detalhe.isNotBlank()) add(bloco(detalhe, tinta(fonteDoDetalhe, TAMANHO_DO_DETALHE)))
        }

        // A imagem tem a largura da linha mais larga, para o texto encostar de verdade na margem esquerda.
        var linhaMaisLarga = 1f
        for (b in blocos) for (i in 0 until b.lineCount) linhaMaisLarga = maxOf(linhaMaisLarga, b.getLineWidth(i))
        val largura = ceil(linhaMaisLarga).toInt() + 2 * RESPIRO
        val altura = blocos.sumOf { it.height } + 2 * RESPIRO

        val imagem = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        val tela = Canvas(imagem)
        tela.translate(RESPIRO.toFloat(), RESPIRO.toFloat())
        for (b in blocos) {
            b.draw(tela)
            tela.translate(0f, b.height.toFloat())
        }
        return imagem
    }

    /** A legenda subindo e aparecendo no começo do clipe; depois fica parada no lugar. */
    private class LegendaEntrando(private val imagem: Bitmap) : BitmapOverlay() {
        private var primeiroUs = Long.MIN_VALUE

        override fun getBitmap(presentationTimeUs: Long): Bitmap = imagem

        override fun getOverlaySettings(presentationTimeUs: Long): StaticOverlaySettings {
            if (primeiroUs == Long.MIN_VALUE) primeiroUs = presentationTimeUs
            return posicaoDaLegenda(entradaDaLegenda(presentationTimeUs - primeiroUs))
        }
    }

    private companion object {
        /** Quadro do vídeo final: em pé, 1080p, a mesma qualidade em que os clipes são gravados. */
        const val LARGURA = 1080
        const val ALTURA = 1920
        const val TAMANHO_DO_TITULO = 66f
        const val TAMANHO_DO_DETALHE = 45f

        /** Distância da legenda até as bordas, na escala do quadro (2 = o quadro inteiro). */
        const val MARGEM_NO_QUADRO = 0.07f

        /** Folga em volta do texto dentro da imagem, para a sombra não ser cortada. */
        const val RESPIRO = 12
        const val SOMBRA = 9f

        /** Quanto a legenda sobe ao entrar, na escala do quadro. Não passa da borda (-1). */
        const val SUBIDA_DA_LEGENDA = 0.06f

        /**
         * Onde a legenda fica: canto inferior esquerdo. As coordenadas vão de -1
         * (embaixo/esquerda) a 1 (em cima/direita).
         *
         * @param entrou de 0 (entrando, mais baixa e transparente) a 1 (no lugar)
         */
        fun posicaoDaLegenda(entrou: Float): StaticOverlaySettings {
            val y = -1f + MARGEM_NO_QUADRO - SUBIDA_DA_LEGENDA * (1f - entrou)
            return StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(-1f + MARGEM_NO_QUADRO, y.coerceIn(-1f, 1f))
                .setOverlayFrameAnchor(-1f, -1f)
                .setAlphaScale(entrou.coerceIn(0f, 1f))
                .build()
        }
    }
}

/** Velocidade igual do começo ao fim do pedaço (a câmera lenta é um pedaço só). */
@OptIn(UnstableApi::class)
private class VelocidadeFixa(private val velocidade: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = velocidade
    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}

/**
 * O fade da transição: escurece até o preto no fim do pedaço e clareia no começo.
 *
 * O tempo conta do primeiro quadro que chega, então não depende de como o Media3 numera
 * os quadros de cada pedaço.
 */
@OptIn(UnstableApi::class)
private class Fade(
    private val duracaoUs: Long,
    private val entrada: Boolean,
    private val saida: Boolean,
) : RgbMatrix {
    private var primeiroUs = Long.MIN_VALUE
    private val matriz = FloatArray(16)

    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        if (primeiroUs == Long.MIN_VALUE) primeiroUs = presentationTimeUs
        val b = brilhoDoFade(presentationTimeUs - primeiroUs, duracaoUs, entrada, saida)
        android.opengl.Matrix.setIdentityM(matriz, 0)
        android.opengl.Matrix.scaleM(matriz, 0, b, b, b)
        return matriz.copyOf()
    }
}
