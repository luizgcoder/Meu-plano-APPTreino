package com.luiz.meuplano.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesDaObservacaoTest {

    @Test
    fun serieEscritaNoParagrafoViraSerie() {
        val r = SerieParser.seriesDaObservacao(
            "1x1- peso corporal (Gravar) pode testar mais de uma vez se caso notar que talvez consiga.",
        )
        assertEquals(1, r.series.size)
        val s = r.series[0]
        assertTrue(s.interpretada)
        assertEquals(1, s.series)
        assertEquals(1, s.repsMin)
        assertTrue(s.pesoCorporal)
        assertTrue(s.gravar)
        assertNull(r.observacao)
    }

    @Test
    fun recadoContinuaObservacao() {
        val r = SerieParser.seriesDaObservacao("Igual semana passada\n3x5 — 80kg RPE 7")
        assertEquals(1, r.series.size)
        assertEquals("Igual semana passada", r.observacao)
    }

    @Test
    fun semFormatoViraSerieLivre() {
        val r = SerieParser.seriesDaObservacao("Fazer até a falha, gravar")
        assertEquals(1, r.series.size)
        assertFalse(r.series[0].interpretada)
        assertTrue(r.series[0].gravar)
        assertEquals("Fazer até a falha, gravar", r.observacao)
    }

    @Test
    fun semObservacaoNenhumaAindaTemSerie() {
        val r = SerieParser.seriesDaObservacao(null)
        assertEquals(1, r.series.size)
        assertFalse(r.series[0].gravar)
    }
}
