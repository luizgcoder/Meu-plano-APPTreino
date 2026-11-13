package com.luiz.meuplano.video

import kotlin.math.roundToInt

/*
 * O visual do vídeo de academia, na linha dos vídeos do David Laid, só que discreto:
 * cor um pouco mais contida, contraste com preto mais firme, sombras puxando para o
 * frio e luzes levemente quentes. A pele continua com cara de pele.
 *
 * A conta fica aqui, em Kotlin puro, e vira uma tabela de cores (LUT) que a câmera
 * aplica na hora, na tela e no vídeo. Os valores entram e saem com gama (o espaço em
 * que o Media3 entrega os quadros de vídeo comum), de 0 a 1.
 */

/** Quanto do visual aplicar. [forca] multiplica todos os ajustes; 0 deixa a imagem como veio. */
enum class NivelDoVisual(val rotulo: String, val forca: Float) {
    DESLIGADO("Sem", 0f),
    LEVE("Leve", 1f),
    FORTE("Forte", 2.2f),
}

// Os ajustes na força 1 (o "Leve").
private const val MENOS_COR = 0.16f
private const val CONTRASTE = 0.24f
private const val PRETO = 0.025f
private const val FRIO_NAS_SOMBRAS = 0.035f
private const val QUENTE_NAS_LUZES = 0.02f

private fun limitar(x: Float): Float = x.coerceIn(0f, 1f)

private fun luma(r: Float, g: Float, b: Float): Float = 0.2126f * r + 0.7152f * g + 0.0722f * b

/** Curva em S suave: escurece um pouco as sombras e clareia um pouco as luzes. */
private fun curvaEmS(x: Float): Float = x * x * (3f - 2f * x)

/**
 * Uma cor depois do visual.
 *
 * @return vermelho, verde e azul, de 0 a 1
 */
fun aplicarVisual(r: Float, g: Float, b: Float, forca: Float): FloatArray {
    if (forca <= 0f) return floatArrayOf(r, g, b)

    // 1. Menos cor: puxa cada canal para o cinza da mesma luminosidade.
    val y = luma(r, g, b)
    val manter = 1f - MENOS_COR * forca
    var vr = y + (r - y) * manter
    var vg = y + (g - y) * manter
    var vb = y + (b - y) * manter

    // 2. Contraste em S.
    val k = (CONTRASTE * forca).coerceAtMost(1f)
    vr += k * (curvaEmS(limitar(vr)) - vr)
    vg += k * (curvaEmS(limitar(vg)) - vg)
    vb += k * (curvaEmS(limitar(vb)) - vb)

    // 3. Preto mais firme: o que já era quase preto vira preto.
    val corte = PRETO * forca
    vr = (vr - corte) / (1f - corte)
    vg = (vg - corte) / (1f - corte)
    vb = (vb - corte) / (1f - corte)

    // 4. Sombras frias (azul-petróleo) e luzes quentes, pesando pela luminosidade.
    val luz = limitar(luma(vr, vg, vb))
    val sombra = (1f - luz) * (1f - luz)
    val alta = luz * luz
    val frio = FRIO_NAS_SOMBRAS * forca * sombra
    val quente = QUENTE_NAS_LUZES * forca * alta
    vr += -frio + quente
    vg += frio * 0.25f + quente * 0.35f
    vb += frio * 0.8f - quente

    return floatArrayOf(limitar(vr), limitar(vg), limitar(vb))
}

/**
 * A tabela de cores do visual: um cubo de [tamanho]³ cores no formato ARGB, indexado
 * por [vermelho][verde][azul], como o SingleColorLut do Media3 espera.
 */
fun cuboDoVisual(forca: Float, tamanho: Int = 33): Array<Array<IntArray>> {
    val passo = 1f / (tamanho - 1)
    return Array(tamanho) { ir ->
        Array(tamanho) { ig ->
            IntArray(tamanho) { ib ->
                val (r, g, b) = aplicarVisual(ir * passo, ig * passo, ib * passo, forca)
                (0xFF shl 24) or
                    ((r * 255f).roundToInt() shl 16) or
                    ((g * 255f).roundToInt() shl 8) or
                    (b * 255f).roundToInt()
            }
        }
    }
}

/**
 * Aplica a tabela de cores em pixels ARGB, no lugar, com interpolação entre os pontos
 * do cubo (para não aparecer degrau nas cores). É o que dá à foto o mesmo visual do vídeo.
 * Feito sem criar objetos por pixel, porque uma foto tem milhões deles.
 */
fun aplicarCubo(pixels: IntArray, cubo: Array<Array<IntArray>>) {
    val maximo = cubo.size - 1
    for (i in pixels.indices) {
        val p = pixels[i]
        val r = ((p shr 16) and 0xFF) * maximo / 255f
        val g = ((p shr 8) and 0xFF) * maximo / 255f
        val b = (p and 0xFF) * maximo / 255f
        val r0 = r.toInt().coerceAtMost(maximo - 1)
        val g0 = g.toInt().coerceAtMost(maximo - 1)
        val b0 = b.toInt().coerceAtMost(maximo - 1)
        val fr = r - r0
        val fg = g - g0
        val fb = b - b0
        val p000 = cubo[r0][g0][b0]
        val p001 = cubo[r0][g0][b0 + 1]
        val p010 = cubo[r0][g0 + 1][b0]
        val p011 = cubo[r0][g0 + 1][b0 + 1]
        val p100 = cubo[r0 + 1][g0][b0]
        val p101 = cubo[r0 + 1][g0][b0 + 1]
        val p110 = cubo[r0 + 1][g0 + 1][b0]
        val p111 = cubo[r0 + 1][g0 + 1][b0 + 1]
        var saida = p and -0x1000000
        var desloc = 16
        while (desloc >= 0) {
            val c000 = ((p000 shr desloc) and 0xFF).toFloat()
            val c001 = ((p001 shr desloc) and 0xFF).toFloat()
            val c010 = ((p010 shr desloc) and 0xFF).toFloat()
            val c011 = ((p011 shr desloc) and 0xFF).toFloat()
            val c100 = ((p100 shr desloc) and 0xFF).toFloat()
            val c101 = ((p101 shr desloc) and 0xFF).toFloat()
            val c110 = ((p110 shr desloc) and 0xFF).toFloat()
            val c111 = ((p111 shr desloc) and 0xFF).toFloat()
            val c00 = c000 + (c001 - c000) * fb
            val c01 = c010 + (c011 - c010) * fb
            val c10 = c100 + (c101 - c100) * fb
            val c11 = c110 + (c111 - c110) * fb
            val c0 = c00 + (c01 - c00) * fg
            val c1 = c10 + (c11 - c10) * fg
            val v = (c0 + (c1 - c0) * fr + 0.5f).toInt().coerceIn(0, 255)
            saida = saida or (v shl desloc)
            desloc -= 8
        }
        pixels[i] = saida
    }
}
