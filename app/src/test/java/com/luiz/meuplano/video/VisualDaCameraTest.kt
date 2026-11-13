package com.luiz.meuplano.video

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualDaCameraTest {

    private fun luma(c: FloatArray) = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]
    private fun saturacao(c: FloatArray) = c.max() - c.min()

    @Test
    fun `desligado deixa a cor como veio`() {
        assertArrayEquals(floatArrayOf(0.8f, 0.3f, 0.1f), aplicarVisual(0.8f, 0.3f, 0.1f, 0f), 0f)
    }

    @Test
    fun `preto continua preto e branco continua quase branco`() {
        val preto = aplicarVisual(0f, 0f, 0f, NivelDoVisual.FORTE.forca)
        assertTrue(preto.all { it < 0.03f })
        val branco = aplicarVisual(1f, 1f, 1f, NivelDoVisual.FORTE.forca)
        assertTrue(branco.all { it > 0.95f })
    }

    @Test
    fun `a luminosidade nunca inverte`() {
        for (nivel in NivelDoVisual.entries) {
            var anterior = -1f
            for (i in 0..100) {
                val v = i / 100f
                val y = luma(aplicarVisual(v, v, v, nivel.forca))
                assertTrue("${nivel.name} em $v", y >= anterior - 1e-4f)
                anterior = y
            }
        }
    }

    @Test
    fun `o leve tira um pouco de cor e o forte tira mais`() {
        val pele = floatArrayOf(0.85f, 0.62f, 0.5f)
        val leve = aplicarVisual(pele[0], pele[1], pele[2], NivelDoVisual.LEVE.forca)
        val forte = aplicarVisual(pele[0], pele[1], pele[2], NivelDoVisual.FORTE.forca)
        assertTrue(saturacao(leve) < saturacao(pele))
        assertTrue(saturacao(forte) < saturacao(leve))
        // A pele continua quente: o vermelho segue na frente.
        assertTrue(forte[0] > forte[1] && forte[1] > forte[2])
    }

    @Test
    fun `sombras puxam para o frio`() {
        val sombra = aplicarVisual(0.2f, 0.2f, 0.2f, NivelDoVisual.LEVE.forca)
        assertTrue(sombra[2] > sombra[0])
    }

    @Test
    fun `o cubo tem o formato do SingleColorLut`() {
        val cubo = cuboDoVisual(NivelDoVisual.LEVE.forca)
        assertEquals(33, cubo.size)
        assertEquals(33, cubo[0].size)
        assertEquals(33, cubo[0][0].size)
        // Opaco, e indexado por [vermelho][verde][azul].
        assertTrue(cubo.all { plano -> plano.all { linha -> linha.all { (it ushr 24) == 0xFF } } })
        val soVermelho = cubo[32][0][0]
        assertTrue(((soVermelho shr 16) and 0xFF) > 200 && (soVermelho and 0xFF) < 60)
        val soAzul = cubo[0][0][32]
        assertTrue((soAzul and 0xFF) > 200 && ((soAzul shr 16) and 0xFF) < 60)
    }
}
