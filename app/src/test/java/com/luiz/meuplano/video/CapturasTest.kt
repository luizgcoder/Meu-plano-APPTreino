package com.luiz.meuplano.video

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturasTest {

    @Test
    fun `nome vai e volta`() {
        val nome = nomeDaCaptura(Marca.BACKOFF, 1_760_400_000_000, foto = false)
        assertEquals("BACKOFF_1760400000000.mp4", nome)
        val lida = lerCaptura(File("/x", nome))!!
        assertEquals(Marca.BACKOFF, lida.marca)
        assertEquals(1_760_400_000_000, lida.criadaEm)
        assertEquals(false, lida.foto)
        assertTrue(lerCaptura(File(nomeDaCaptura(Marca.AQUECIMENTO, 1_760_400_000_001, foto = true)))!!.foto)
    }

    @Test
    fun `arquivos estranhos ficam de fora e a ordem e do mais novo`() {
        assertNull(lerCaptura(File("foto_123.jpg")))
        assertNull(lerCaptura(File("OUTRA_1760400000000.mp4")))
        val lista = capturasDaPasta(
            listOf(File("LIVRE_1760400000000.jpg"), File("lixo.tmp"), File("AQUECIMENTO_1760400009999.mp4")),
        )
        assertEquals(listOf("AQUECIMENTO_1760400009999.mp4", "LIVRE_1760400000000.jpg"), lista.map { it.arquivo.name })
    }

    @Test
    fun `o visual na foto bate com o da tabela`() {
        val cubo = cuboDoVisual(NivelDoVisual.LEVE.forca)
        // Nos pontos exatos do cubo, a foto recebe exatamente a cor da tabela.
        val pixels = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0x80FF0000.toInt())
        aplicarCubo(pixels, cubo)
        assertEquals(cubo[0][0][0], pixels[0])
        assertEquals(cubo[32][32][32], pixels[1])
        // O alfa da foto é mantido.
        assertEquals(0x80, pixels[2] ushr 24)
        assertEquals(cubo[32][0][0] and 0xFFFFFF, pixels[2] and 0xFFFFFF)
        // Entre os pontos, fica perto da conta direta do visual.
        val meio = intArrayOf(0xFF8A5F49.toInt())
        aplicarCubo(meio, cubo)
        val esperado = aplicarVisual(0x8A / 255f, 0x5F / 255f, 0x49 / 255f, NivelDoVisual.LEVE.forca)
        val r = (meio[0] shr 16) and 0xFF
        assertTrue("vermelho $r", kotlin.math.abs(r - esperado[0] * 255) < 3)
    }
}
