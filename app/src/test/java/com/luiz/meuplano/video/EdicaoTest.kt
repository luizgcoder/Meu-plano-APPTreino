package com.luiz.meuplano.video

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdicaoTest {

    @Test
    fun `sem lento e um pedaco so, ate o fim do arquivo`() {
        assertEquals(listOf(Pedaco(0, null, 1f, primeiro = true, ultimo = true)), pedacosDoClipe(EdicaoDoClipe(), 10_000))
        assertEquals(
            listOf(Pedaco(2_000, 8_000, 1f, primeiro = true, ultimo = true)),
            pedacosDoClipe(EdicaoDoClipe(inicioMs = 2_000, fimMs = 8_000), 10_000),
        )
    }

    @Test
    fun `lento no meio vira tres pedacos`() {
        val e = EdicaoDoClipe(inicioMs = 1_000, fimMs = 9_000, lentoInicioMs = 3_000, lentoFimMs = 5_000)
        assertEquals(
            listOf(
                Pedaco(1_000, 3_000, 1f, primeiro = true, ultimo = false),
                Pedaco(3_000, 5_000, VELOCIDADE_LENTA, primeiro = false, ultimo = false),
                Pedaco(5_000, 9_000, 1f, primeiro = false, ultimo = true),
            ),
            pedacosDoClipe(e, 10_000),
        )
        // 2 s + 4 s (2 s em câmera lenta) + 4 s
        assertEquals(10_000L, duracaoDoClipeNaSaidaMs(e, 10_000))
        assertEquals(11_000L, duracaoDoClipeNaSaidaMs(e.copy(congelarFinal = true), 10_000))
    }

    @Test
    fun `lento encostado nas pontas engole os restos curtos`() {
        val e = EdicaoDoClipe(lentoInicioMs = 50, lentoFimMs = 9_950)
        // Sobram 50 ms de cada lado: tudo vira lento, até o fim do arquivo.
        assertEquals(listOf(Pedaco(0, null, VELOCIDADE_LENTA, primeiro = true, ultimo = true)), pedacosDoClipe(e, 10_000))
    }

    @Test
    fun `lento fora do corte ou curto demais e ignorado`() {
        val curto = EdicaoDoClipe(inicioMs = 0, fimMs = 5_000, lentoInicioMs = 1_000, lentoFimMs = 1_100)
        assertEquals(1, pedacosDoClipe(curto, 10_000).size)
        val fora = EdicaoDoClipe(inicioMs = 0, fimMs = 5_000, lentoInicioMs = 6_000, lentoFimMs = 8_000)
        assertNull(fora.ajustada(10_000).lentoInicioMs)
        // Metade dentro: fica só a parte dentro do corte.
        val metade = EdicaoDoClipe(inicioMs = 0, fimMs = 5_000, lentoInicioMs = 4_000, lentoFimMs = 8_000).ajustada(10_000)
        assertEquals(4_000L to 5_000L, metade.lentoInicioMs to metade.lentoFimMs)
    }

    @Test
    fun `zoom vira recorte dentro da imagem`() {
        assertNull(recorteDoZoom(1f, 0.5f, 0.5f))
        assertArrayEquals(floatArrayOf(-0.5f, 0.5f, -0.5f, 0.5f), recorteDoZoom(2f, 0.5f, 0.5f), 1e-5f)
        // Centro encostado no canto de cima à esquerda: o recorte para na borda.
        assertArrayEquals(floatArrayOf(-1f, 0f, 0f, 1f), recorteDoZoom(2f, 0f, 0f), 1e-5f)
        assertEquals(0.25f, limitarCentro(0.1f, 2f), 1e-6f)
        assertEquals(0.5f, limitarCentro(0.9f, 1f), 1e-6f)
    }

    @Test
    fun `fade escurece nas pontas e fica normal no meio`() {
        val d = 2_000_000L
        assertEquals(0f, brilhoDoFade(0, d, entrada = true, saida = true), 1e-6f)
        assertEquals(0.5f, brilhoDoFade(FADE_US / 2, d, entrada = true, saida = true), 1e-3f)
        assertEquals(1f, brilhoDoFade(1_000_000, d, entrada = true, saida = true), 1e-6f)
        assertEquals(0f, brilhoDoFade(d, d, entrada = true, saida = true), 1e-6f)
        assertEquals(1f, brilhoDoFade(0, d, entrada = false, saida = true), 1e-6f)
        // Pedaço curto: o fade fica com um terço dele, para ainda aparecer imagem.
        assertEquals(1f, brilhoDoFade(150_000, 300_000, entrada = true, saida = true), 1e-6f)
    }

    @Test
    fun `legenda entra e para no lugar`() {
        assertEquals(0f, entradaDaLegenda(0), 1e-6f)
        assertTrue(entradaDaLegenda(ENTRADA_DA_LEGENDA_US / 2) > 0.5f)
        assertEquals(1f, entradaDaLegenda(ENTRADA_DA_LEGENDA_US * 3), 1e-6f)
    }

    @Test
    fun `miniaturas espalhadas pelo video`() {
        assertEquals(listOf(625L, 1_875L, 3_125L, 4_375L), temposDasMiniaturas(5_000, 4))
        assertEquals(emptyList<Long>(), temposDasMiniaturas(0, 4))
    }

    @Test
    fun `musica sem arquivo vira mudo, e simplificar tira os efeitos`() {
        assertEquals(SomDoVideo.MUDO, OpcoesDaMontagem(som = SomDoVideo.MUSICA).somEfetivo)
        val s = OpcoesDaMontagem(som = SomDoVideo.ORIGINAL).simplificada()
        assertEquals(false to false, s.transicoes to s.legendaAnimada)
        assertEquals(SomDoVideo.ORIGINAL, s.som)
    }
}
