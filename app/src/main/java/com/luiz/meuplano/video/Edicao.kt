package com.luiz.meuplano.video

import kotlin.math.max
import kotlin.math.min

/*
 * As regras de edição dos clipes, em Kotlin puro: o que vira câmera lenta, o enquadramento
 * do zoom, o fade da transição e a entrada da legenda. A montagem (MontadorDeVideo) e a
 * tela de corte só usam o que sai daqui, então a conta é a mesma nas duas.
 *
 * Tempos do clipe em milissegundos, como os do corte; tempos dos efeitos em microssegundos,
 * como os do Media3.
 */

/** Velocidade da câmera lenta. */
const val VELOCIDADE_LENTA = 0.5f

/** Quanto o último quadro fica parado quando o clipe tem "congelar o final". */
const val CONGELAMENTO_MS = 1_000L

/** Duração do fade entre um clipe e outro (sai para o preto e volta). */
const val FADE_US = 220_000L

/** Tempo da legenda entrando na tela. */
const val ENTRADA_DA_LEGENDA_US = 450_000L

/** Um quadro a 30 fps, o passo do "quadro a quadro" na tela de corte. */
const val UM_QUADRO_MS = 33L

/** Trechos menores que isso viram parte do vizinho, para não sobrar um pedaço de quadro solto. */
const val MENOR_PEDACO_MS = 120L

/** Menor trecho em câmera lenta que vale a pena. */
const val MENOR_LENTO_MS = 200L

const val ZOOM_MAXIMO = 2.5f

/**
 * O que foi editado em um clipe.
 *
 * @param inicioMs corte; os dois nulos = vídeo inteiro
 * @param lentoInicioMs trecho em câmera lenta, dentro do corte; os dois nulos = sem lento
 * @param zoom 1 = sem zoom
 * @param centroX centro do enquadramento do zoom, de 0 (esquerda) a 1 (direita)
 * @param centroY centro do enquadramento do zoom, de 0 (em cima) a 1 (embaixo)
 */
data class EdicaoDoClipe(
    val inicioMs: Long? = null,
    val fimMs: Long? = null,
    val lentoInicioMs: Long? = null,
    val lentoFimMs: Long? = null,
    val congelarFinal: Boolean = false,
    val zoom: Float = 1f,
    val centroX: Float = 0.5f,
    val centroY: Float = 0.5f,
    /** Velocidade do trecho em câmera lenta: ¼×, ½× ou ¾× do normal. */
    val velocidadeLenta: Float = VELOCIDADE_LENTA,
    /** Vira o vídeo na horizontal (espelhado), do início ao fim do clipe. */
    val espelhado: Boolean = false,
) {
    val temLento: Boolean get() = lentoInicioMs != null && lentoFimMs != null
    val temZoom: Boolean get() = zoom > 1.01f

    /**
     * A edição arrumada para um vídeo de [duracaoMs]: corte dentro do vídeo, lento dentro
     * do corte (ou nenhum, se ficou curto demais) e enquadramento dentro da imagem.
     */
    fun ajustada(duracaoMs: Long): EdicaoDoClipe {
        val inicio = (inicioMs ?: 0L).coerceIn(0L, duracaoMs)
        val fim = (fimMs ?: duracaoMs).coerceIn(inicio, duracaoMs)
        val lentoIni = lentoInicioMs?.coerceIn(inicio, fim)
        val lentoFim = lentoFimMs?.coerceIn(inicio, fim)
        val lentoValido = lentoIni != null && lentoFim != null && lentoFim - lentoIni >= MENOR_LENTO_MS
        val z = zoom.coerceIn(1f, ZOOM_MAXIMO)
        return EdicaoDoClipe(
            inicioMs = inicioMs?.let { inicio },
            fimMs = fimMs?.let { fim },
            lentoInicioMs = if (lentoValido) lentoIni else null,
            lentoFimMs = if (lentoValido) lentoFim else null,
            congelarFinal = congelarFinal,
            zoom = z,
            centroX = limitarCentro(centroX, z),
            centroY = limitarCentro(centroY, z),
            velocidadeLenta = velocidadeLenta.coerceIn(0.1f, 0.9f),
            espelhado = espelhado,
        )
    }
}

/**
 * Um pedaço do clipe na montagem.
 *
 * @param fimMs nulo = até o fim do arquivo
 * @param primeiro o primeiro pedaço do clipe (recebe o fade de entrada e a legenda entrando)
 * @param ultimo o último pedaço com vídeo (recebe o fade de saída, se não houver congelamento)
 */
data class Pedaco(
    val inicioMs: Long,
    val fimMs: Long?,
    val velocidade: Float,
    val primeiro: Boolean,
    val ultimo: Boolean,
) {
    val lento: Boolean get() = velocidade < 1f

    /** Quanto o pedaço dura no vídeo final, já com a câmera lenta. */
    fun duracaoNaSaidaMs(duracaoDoArquivoMs: Long): Long {
        val fim = fimMs ?: duracaoDoArquivoMs
        return ((fim - inicioMs).coerceAtLeast(0L) / velocidade).toLong()
    }
}

/**
 * Os pedaços em que o clipe é montado: o trecho antes do lento, o lento e o trecho depois.
 * Sem lento, um pedaço só. Pedaços curtos demais nas pontas são engolidos pelo lento.
 */
fun pedacosDoClipe(edicao: EdicaoDoClipe, duracaoMs: Long): List<Pedaco> {
    val e = edicao.ajustada(duracaoMs)
    val inicio = e.inicioMs ?: 0L
    // Sem corte no fim, o último pedaço vai até o fim do arquivo (nulo), sem arredondar.
    val fim = e.fimMs
    val lentoIni = e.lentoInicioMs
    val lentoFim = e.lentoFimMs
    if (lentoIni == null || lentoFim == null) {
        return listOf(Pedaco(inicio, fim, 1f, primeiro = true, ultimo = true))
    }
    val fimReal = fim ?: duracaoMs
    val antes = lentoIni - inicio
    val depois = fimReal - lentoFim
    val comecoDoLento = if (antes < MENOR_PEDACO_MS) inicio else lentoIni
    val finalDoLento: Long? = if (depois < MENOR_PEDACO_MS) fim else lentoFim

    val pedacos = mutableListOf<Pedaco>()
    if (comecoDoLento > inicio) pedacos += Pedaco(inicio, comecoDoLento, 1f, primeiro = false, ultimo = false)
    pedacos += Pedaco(comecoDoLento, finalDoLento, e.velocidadeLenta, primeiro = false, ultimo = false)
    if (finalDoLento != null && finalDoLento < fimReal) {
        pedacos += Pedaco(finalDoLento, fim, 1f, primeiro = false, ultimo = false)
    }
    return pedacos.mapIndexed { i, p -> p.copy(primeiro = i == 0, ultimo = i == pedacos.lastIndex) }
}

/** Quanto o clipe inteiro dura no vídeo final: pedaços (com o lento) e o congelamento. */
fun duracaoDoClipeNaSaidaMs(edicao: EdicaoDoClipe, duracaoMs: Long): Long =
    pedacosDoClipe(edicao, duracaoMs).sumOf { it.duracaoNaSaidaMs(duracaoMs) } +
        if (edicao.congelarFinal) CONGELAMENTO_MS else 0L

/** O centro do zoom que mantém o enquadramento dentro da imagem (0 a 1). */
fun limitarCentro(centro: Float, zoom: Float): Float {
    val metade = 0.5f / zoom.coerceAtLeast(1f)
    return centro.coerceIn(metade, 1f - metade)
}

/**
 * O enquadramento do zoom em coordenadas do Media3 (de -1 a 1, com o y para cima):
 * esquerda, direita, embaixo, em cima. Nulo sem zoom.
 */
fun recorteDoZoom(zoom: Float, centroX: Float, centroY: Float): FloatArray? {
    if (zoom <= 1.01f) return null
    val z = zoom.coerceAtMost(ZOOM_MAXIMO)
    val cx = limitarCentro(centroX, z) * 2f - 1f
    // Na tela o y cresce para baixo; no Media3, para cima.
    val cy = 1f - limitarCentro(centroY, z) * 2f
    val meio = 1f / z
    return floatArrayOf(cx - meio, cx + meio, cy - meio, cy + meio)
}

/**
 * O brilho do quadro no fade da transição: 0 = preto, 1 = normal.
 *
 * @param tUs tempo do quadro desde o começo do pedaço
 * @param duracaoUs duração do pedaço no vídeo final
 */
fun brilhoDoFade(tUs: Long, duracaoUs: Long, entrada: Boolean, saida: Boolean, fadeUs: Long = FADE_US): Float {
    // Em pedaço muito curto, cada fade fica com no máximo um terço dele.
    val f = min(fadeUs, max(1L, duracaoUs / 3))
    var b = 1f
    if (entrada) b = min(b, tUs.toFloat() / f)
    if (saida) b = min(b, (duracaoUs - tUs).toFloat() / f)
    return b.coerceIn(0f, 1f)
}

/** Quanto a legenda já entrou: de 0 (fora) a 1 (no lugar), desacelerando no fim. */
fun entradaDaLegenda(tUs: Long, duracaoUs: Long = ENTRADA_DA_LEGENDA_US): Float {
    val x = (tUs.toFloat() / duracaoUs).coerceIn(0f, 1f)
    val falta = 1f - x
    return 1f - falta * falta * falta
}

/** Os momentos (ms) das miniaturas da barra de corte, no meio de cada fatia do vídeo. */
fun temposDasMiniaturas(duracaoMs: Long, quantas: Int): List<Long> {
    if (duracaoMs <= 0 || quantas <= 0) return emptyList()
    return (0 until quantas).map { i -> (duracaoMs * (2 * i + 1)) / (2L * quantas) }
}

/** O som do vídeo montado. */
enum class SomDoVideo(val rotulo: String) {
    MUDO("Mudo"),
    ORIGINAL("Original"),
    MUSICA("Música"),
}

/**
 * Como montar o vídeo de cada treino.
 *
 * @param musica arquivo da música de fundo (só vale com [SomDoVideo.MUSICA])
 * @param nomeDaMusica o nome do arquivo como estava no celular, para mostrar na tela
 * @param mudadasEm quando as opções mudaram (ms): vídeo montado antes disso fica desatualizado
 */
data class OpcoesDaMontagem(
    val transicoes: Boolean = true,
    val legendaAnimada: Boolean = true,
    val som: SomDoVideo = SomDoVideo.MUDO,
    val musica: java.io.File? = null,
    val nomeDaMusica: String? = null,
    val mudadasEm: Long = 0L,
) {
    /** O som que realmente vai: música escolhida sem arquivo vira mudo. */
    val somEfetivo: SomDoVideo get() = if (som == SomDoVideo.MUSICA && musica == null) SomDoVideo.MUDO else som

    /** A versão mais simples, para tentar de novo quando o aparelho não aguenta os efeitos. */
    fun simplificada(): OpcoesDaMontagem = copy(transicoes = false, legendaAnimada = false)
}
