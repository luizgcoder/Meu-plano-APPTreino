package com.luiz.meuplano.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Confere o parser contra o plano de exemplo (src/test/resources/plano_exemplo.html): um plano
 * inventado, sem dados de ninguém, com o mesmo formato do HTML que o treinador envia.
 */
class PlanoParserTest {

    private val plano: Plano by lazy {
        val html = javaClass.getResourceAsStream("/plano_exemplo.html")!!
            .use { it.readBytes().toString(Charsets.UTF_8) }
        PlanoParser.parse(html)
    }

    private fun exercicio(treino: String, nome: String): Exercicio =
        plano.treinos.first { it.letra == treino }.exercicios.first { it.nome == nome }

    @Test
    fun cabecalho() {
        assertEquals("Aluno Exemplo", plano.nomeAluno)
        assertEquals("2026-03-02T08:30", plano.geradoEm)
        assertEquals(3, plano.semana)
        assertTrue(plano.avisos.toString(), plano.avisos.isEmpty())
    }

    @Test
    fun contagens() {
        assertEquals(listOf("A", "B", "C", "D", "E"), plano.treinos.map { it.letra })
        assertEquals(listOf(5, 4, 5, 2, 3), plano.treinos.map { it.exercicios.size })
        val series = plano.treinos.flatMap { it.exercicios }.flatMap { it.series }
        assertEquals(29, series.size)
        assertEquals(13, series.count { it.gravar })
        assertEquals(8, series.count { it.informarCarga })
        assertTrue(series.all { it.interpretada })
    }

    @Test
    fun refeicoes() {
        assertEquals(4, plano.refeicoes.size)
        assertEquals(listOf(2, 1, 3, 1), plano.refeicoes.map { it.opcoes.size })
        val primeira = plano.refeicoes[0]
        assertEquals("🌅 1ª Refeição", primeira.titulo)
        assertEquals("Opção A (Ovos + Tapioca)", primeira.opcoes[0].nome)
        assertEquals(4, primeira.opcoes[0].itens.size)
        assertEquals("3 ovos", primeira.opcoes[0].itens[0].texto)
        assertFalse(primeira.opcoes[0].itens[0].opcional)
        assertTrue(primeira.opcoes[0].itens[1].opcional)
        // A 2ª refeição não tem "Opção A/B": é uma lista só.
        assertNull(plano.refeicoes[1].opcoes[0].nome)
        assertEquals(4, plano.refeicoes[1].opcoes[0].itens.size)
    }

    @Test
    fun prs() {
        assertEquals(6, plano.prs.size)
        assertEquals("Deadlift", plano.prs[0].exercicio)
        assertEquals(150.0, plano.prs[0].cargaKg!!, 0.0)
        assertEquals("120kg (125kg novo)", plano.prs[2].texto)
        assertEquals(120.0, plano.prs[2].cargaKg!!, 0.0)
    }

    @Test
    fun metas() {
        assertEquals(3.0, plano.metas.aguaLitros!!, 0.0)
        assertEquals(3, plano.metas.cardioDiasSemana)
        assertEquals(20, plano.metas.cardioMinutos)
    }

    @Test
    fun blocosDeTexto() {
        assertTrue(plano.blocos.any { it.categoria == CategoriaInfo.CARDIO })
        val diretrizes = plano.blocos.first { it.titulo?.contains("Diretrizes da Dieta") == true }
        assertEquals(CategoriaInfo.DIETA, diretrizes.categoria)
        assertTrue(diretrizes.linhas.any { "3L de água" in it })
        // Nenhum texto do treino vazou para os blocos.
        assertTrue(plano.blocos.none { b -> b.linhas.any { "RPE 7 (Gravar" in it } })
    }

    @Test
    fun cargaFixaComObservacaoAbaixo() {
        val s = exercicio("A", "Agachamento high bar").series.single()
        assertEquals("5x2 — 100kg- RPE 7 (Gravar e me informar carga)", s.textoOriginal)
        assertEquals(5, s.series)
        assertEquals(2, s.repsMin)
        assertEquals(2, s.repsMax)
        assertEquals(100.0, s.cargaMinKg!!, 0.0)
        assertEquals(100.0, s.cargaMaxKg!!, 0.0)
        assertEquals(7.0, s.rpeMin!!, 0.0)
        assertTrue(s.gravar)
        assertTrue(s.informarCarga)
        assertEquals(
            "1min de descanso, manter o tronco firme na subida",
            s.observacao,
        )
    }

    @Test
    fun cargaRelativaASerieAnterior() {
        val series = exercicio("A", "Supino pausado").series
        assertEquals(2, series.size)
        assertEquals(7.5, series[0].rpeMin!!, 0.0)
        assertNull(series[0].cargaMinKg)
        assertTrue(series[0].informarCarga)
        assertEquals(2, series[1].series)
        assertEquals(2, series[1].repsMin)
        assertEquals(-10.0, series[1].ajustePercentual!!, 0.0)
        assertFalse(series[1].gravar)
        assertNull(series[1].observacao)
    }

    @Test
    fun gravarSemItalicoESemTravessao() {
        val s = exercicio("A", "Pallof press").series.single()
        assertEquals(2, s.series)
        assertEquals(8, s.repsMin)
        assertEquals(8, s.repsMax)
        assertEquals(8.0, s.rpeMin!!, 0.0)
        assertTrue(s.gravar)
        assertFalse(s.informarCarga)
        assertNull(s.observacao)
    }

    @Test
    fun faixaDeRepeticoesEVideo() {
        val e = exercicio("A", "Remada baixa")
        assertEquals("https://example.com/remada-baixa", e.videoUrl)
        assertEquals("ou Remada curvada", e.observacao)
        val ultima = e.series.last()
        assertEquals(3, ultima.series)
        assertEquals(6, ultima.repsMin)
        assertEquals(8, ultima.repsMax)
        assertEquals(9.0, ultima.rpeMin!!, 0.0)
    }

    @Test
    fun recadoNoNomeViraObservacao() {
        val e = plano.treinos[1].exercicios[0]
        assertEquals("Clean pull (Igual ao da semana passada)", e.nomeOriginal)
        assertEquals("Clean pull", e.nome)
        assertEquals("clean pull", e.chave)
        assertEquals("Igual ao da semana passada", e.observacao)
        // "1x3— 60kg": o 60 é carga, não o fim de uma faixa de repetições.
        assertEquals(3, e.series[0].repsMax)
        assertEquals(60.0, e.series[0].cargaMinKg!!, 0.0)
        assertTrue(e.series[0].gravar)
        // Linha inteira em itálico.
        assertEquals(4, e.series[1].repsMin)
        assertEquals(50.0, e.series[1].cargaMinKg!!, 0.0)
        assertFalse(e.series[1].gravar)
    }

    @Test
    fun variacaoCurtaFicaNoNome() {
        assertNotNull(exercicio("C", "Barra fixa (pronada)"))
        assertEquals("barra fixa (pronada)", exercicio("C", "Barra fixa (pronada)").chave)
        assertNotNull(exercicio("C", "Remada curvada (deadstop)"))
        val martelo = exercicio("C", "Rosca martelo")
        assertEquals("subir sem balançar o tronco", martelo.observacao)
    }

    @Test
    fun faixaDeCarga() {
        val s = exercicio("B", "Agachamento frontal").series.single()
        assertEquals(4, s.series)
        assertEquals(3, s.repsMin)
        assertEquals(60.0, s.cargaMinKg!!, 0.0)
        assertEquals(70.0, s.cargaMaxKg!!, 0.0)
        assertFalse(s.cargaPorLado)
        assertEquals(6.5, s.rpeMin!!, 0.0)
        assertTrue(s.gravar && s.informarCarga)
        assertNull(s.observacao)
    }

    @Test
    fun pesoCorporalComInstrucaoDepois() {
        val e = exercicio("C", "Flexão explosiva")
        assertEquals("https://example.com/flexao-explosiva?v=1", e.videoUrl)
        assertNull(e.observacao)
        val s = e.series.single()
        assertEquals(3, s.series)
        assertEquals(5, s.repsMin)
        assertTrue(s.pesoCorporal)
        assertNull(s.cargaMinKg)
        assertTrue(s.gravar)
        assertFalse(s.informarCarga)
        assertEquals(
            "Manter o quadril alinhado do começo ao fim\n" +
                "empurrar o chão com força para as mãos saírem dele, igual no vídeo de exemplo",
            s.observacao,
        )
    }

    @Test
    fun observacaoDoExercicioForaDoNegrito() {
        val e = exercicio("D", "Agachamento com pausa")
        assertEquals("3seg de descida + pausa de 2 seg embaixo", e.observacao)
        val s = e.series.single()
        assertEquals(4, s.series)
        assertEquals(80.0, s.cargaMinKg!!, 0.0)
        assertEquals(6.5, s.rpeMin!!, 0.0)
    }

    @Test
    fun unidadeDiferenteDeRepeticoes() {
        val e = exercicio("E", "Caminhada do fazendeiro")
        assertEquals("Se preferir trocar me avise", e.observacao)
        val s = e.series.single()
        assertEquals(2, s.series)
        assertEquals(20, s.repsMin)
        assertEquals("passos", s.unidade)
        assertEquals(80.0, s.cargaMinKg!!, 0.0)
        assertTrue(s.gravar)
        assertFalse(s.informarCarga)
        assertEquals("usar straps", s.observacao)
    }

    // ----- formatos que não estão neste plano, mas que o treinador avisa que usa -----

    @Test
    fun cargaPorLado() {
        // "Quando eu mandar a carga no formato 10kg-10kg, estou me referindo a cada lado."
        val s = SerieParser.parse("3x8 — 10kg-10kg — RPE 8")
        assertTrue(s.cargaPorLado)
        assertEquals(10.0, s.cargaMinKg!!, 0.0)
        assertEquals(8.0, s.rpeMin!!, 0.0)
    }

    @Test
    fun variacoesDeEscrita() {
        val faixaComHifen = SerieParser.parse("3x4-6 — RPE 9")
        assertEquals(4, faixaComHifen.repsMin)
        assertEquals(6, faixaComHifen.repsMax)

        val rpeEmFaixa = SerieParser.parse("2x5 - 62,5kg - RPE 7 a 8")
        assertEquals(62.5, rpeEmFaixa.cargaMinKg!!, 0.0)
        assertEquals(7.0, rpeEmFaixa.rpeMin!!, 0.0)
        assertEquals(8.0, rpeEmFaixa.rpeMax!!, 0.0)

        val naoGravar = SerieParser.parse("3x10 — RPE 6 (não precisa gravar)")
        assertFalse(naoGravar.gravar)

        val tempo = SerieParser.parse("3x30seg — peso corporal")
        assertEquals("seg", tempo.unidade)
        assertTrue(tempo.pesoCorporal)
    }

    @Test
    fun linhaForaDoPadraoNaoSePerde() {
        val s = SerieParser.parse("Até a falha com 20kg (Gravar)")
        assertFalse(s.interpretada)
        assertEquals("Até a falha com 20kg (Gravar)", s.textoOriginal)
        assertTrue(s.gravar)
        assertEquals(20.0, s.cargaMinKg!!, 0.0)
    }
}
