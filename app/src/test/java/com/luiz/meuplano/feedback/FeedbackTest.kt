package com.luiz.meuplano.feedback

import com.luiz.meuplano.data.CheckinDiarioEntity
import com.luiz.meuplano.data.ExercicioComSeries
import com.luiz.meuplano.data.ExercicioEntity
import com.luiz.meuplano.data.PlanoCompleto
import com.luiz.meuplano.data.PlanoEntity
import com.luiz.meuplano.data.PrEntity
import com.luiz.meuplano.data.RefeicaoComOpcoes
import com.luiz.meuplano.data.RefeicaoEntity
import com.luiz.meuplano.data.RefeicaoFeitaEntity
import com.luiz.meuplano.data.SerieComRegistro
import com.luiz.meuplano.data.SerieFeitaEntity
import com.luiz.meuplano.data.SeriePrescritaEntity
import com.luiz.meuplano.data.TreinoComExercicios
import com.luiz.meuplano.data.TreinoEntity
import com.luiz.meuplano.parser.SerieParser
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {

    private val sexta = LocalDate.of(2026, 10, 9)
    private var proximoId = 1L

    private fun serie(texto: String): SeriePrescritaEntity {
        val s = SerieParser.parse(texto)
        return SeriePrescritaEntity(
            id = proximoId++, exercicioId = 0, ordem = 0, textoOriginal = s.textoOriginal,
            series = s.series, repsMin = s.repsMin, repsMax = s.repsMax, unidade = s.unidade,
            cargaMinKg = s.cargaMinKg, cargaMaxKg = s.cargaMaxKg, cargaPorLado = s.cargaPorLado,
            pesoCorporal = s.pesoCorporal, ajustePercentual = s.ajustePercentual,
            rpeMin = s.rpeMin, rpeMax = s.rpeMax, gravar = s.gravar, informarCarga = s.informarCarga,
            observacao = s.observacao, interpretada = s.interpretada,
        )
    }

    private fun feita(
        s: SeriePrescritaEntity, kg: Double?, rpe: Double? = null, video: String? = null, nota: String? = null,
    ) = SerieComRegistro(s, SerieFeitaEntity(s.id, kg, rpe, video, null, null, 0, nota))

    private fun naoFeita(s: SeriePrescritaEntity) = SerieComRegistro(s, null)

    private fun exercicio(nome: String, vararg linhas: SerieComRegistro) = ExercicioComSeries(
        ExercicioEntity(proximoId++, 1, 0, nome, nome, nome.lowercase(), null, null),
        linhas.toList(),
    )

    private fun checkin(
        dia: Int, agua: Int = 0, intestino: Boolean? = null, cardio: Boolean = false, furou: Boolean = false,
        fome: Int? = null, peso: Double? = null, nota: String? = null,
    ) = CheckinDiarioEntity("2026-10-%02d".format(dia), agua, intestino, cardio, furou, fome, peso, nota)

    /** A tabela de PRs do plano real. */
    private val prs = listOf(
        "Deadlift" to "200kg", "Bench press" to "110kg", "Squat" to "140kg (144kg novo)",
        "Overhead" to "64kg", "Dip" to "60kg", "Pull" to "20kg (3x25kg novo)(2x30kg novo)",
    ).mapIndexed { i, (nome, texto) ->
        PrEntity(i.toLong(), 1, i, nome, texto, texto.substringBefore("kg").toDouble())
    }

    private fun plano(vararg treinos: TreinoComExercicios): PlanoCompleto {
        val refeicoes = (1L..4L).map { RefeicaoComOpcoes(RefeicaoEntity(it, 1, it.toInt(), "Refeição $it"), emptyList()) }
        return PlanoCompleto(
            PlanoEntity(1, "Luiz", "2026-10-05T19:03", 16, null, null, 4.0, 4, 25, 0, "h"),
            refeicoes, treinos.toList(), prs, emptyList(),
        )
    }

    private fun treino(letra: String, vararg exercicios: ExercicioComSeries) =
        TreinoComExercicios(TreinoEntity(proximoId++, 1, 0, "Treino $letra", letra), exercicios.toList())

    /** Semana parecida com a do feedback real do Luiz: tudo em dia, sem PR, com anotações nos treinos. */
    private fun semanaTranquila(): DadosDaSemana {
        val p = plano(
            treino(
                "A",
                exercicio("Agachamento low bar", feita(serie("8x1 — 90kg- RPE 7 (Gravar e me informar carga)"), 90.0, 7.0, "a.mp4", "Gravei para conferir os detalhes.")),
                exercicio("Paused bench press", feita(serie("1x1 — RPE 7,5 (Gravar e me informar carga)"), 100.0, 7.5, "b.mp4", "Fluindo legal.")),
            ),
            treino(
                "D",
                exercicio("Deadlift sumo", feita(serie("3x3 — RPE 5 (Gravar e me informar carga)"), 150.0, 5.0, "c.mp4", "Apanhei um pouco na execução.")),
            ),
        )
        val checkins = listOf(
            checkin(dia = 5, agua = 4000, intestino = true, cardio = true, fome = 0, peso = 81.9),
            checkin(dia = 6, agua = 4200, intestino = true, cardio = true, fome = 0),
            checkin(dia = 7, agua = 4000, intestino = true, cardio = true, fome = 0),
            checkin(dia = 8, agua = 4100, intestino = true, cardio = true, fome = 0, nota = "A semana foi bem tranquila de modo geral."),
            checkin(dia = 9, agua = 4000, intestino = true, fome = 0, peso = 81.5),
        )
        val refeicoes = buildList {
            for (dia in 5..9) for (refeicao in 1L..4L) add(RefeicaoFeitaEntity("2026-10-%02d".format(dia), refeicao, null))
        }
        return montarDados(p, sexta, checkins, refeicoes)
    }

    @Test
    fun periodo() {
        assertEquals(LocalDate.of(2026, 10, 5) to sexta, periodoDaSemana("2026-10-05T19:03", sexta))
        // Passou de 7 dias: para no 7º dia do plano.
        assertEquals(
            LocalDate.of(2026, 10, 5) to LocalDate.of(2026, 10, 11),
            periodoDaSemana("2026-10-05T19:03", LocalDate.of(2026, 10, 14)),
        )
        // Sem data (ou data no futuro): últimos 7 dias.
        assertEquals(LocalDate.of(2026, 10, 3) to sexta, periodoDaSemana(null, sexta))
        assertEquals(LocalDate.of(2026, 10, 3) to sexta, periodoDaSemana("2026-12-01", sexta))
        assertEquals(5, semanaTranquila().dias)
    }

    @Test
    fun textoNoFormatoDoFeedbackReal() {
        val esperado = """
            FEEDBACK SEMANAL 🏋🏻‍♂️

            Peso: 81.5kg
            Fotos: Abaixo

            ✅ Aderência à dieta: 100%.

            📈 Progressões de Carga:
            * Nenhuma.

            🍽️ Nível de fome: 0

            📝 Observações gerais e como me senti:

            A semana foi bem tranquila de modo geral.

            Destaques dos treinos:
            - Agachamento low bar: Gravei para conferir os detalhes.
            - Paused bench press: Fluindo legal.
            - Deadlift sumo: Apanhei um pouco na execução.
            - Cardio concluído os 4 dias
        """.trimIndent()
        assertEquals(esperado, textoDoFeedback(semanaTranquila()))
    }

    @Test
    fun semanaSemNadaRegistradoMantemOModeloEmBranco() {
        val vazio = montarDados(plano(), sexta, emptyList(), emptyList())
        // A aderência é a exceção: sem nenhum "furei a dieta", a semana conta como 100%.
        val esperado = """
            FEEDBACK SEMANAL 🏋🏻‍♂️

            Peso:
            Fotos: Abaixo

            ✅ Aderência à dieta: 100%.

            📈 Progressões de Carga:
            * Nenhuma.

            🍽️ Nível de fome:

            📝 Observações gerais e como me senti:
        """.trimIndent()
        assertEquals(esperado, textoDoFeedback(vazio))
    }

    @Test
    fun prBatidoEntraNasProgressoes() {
        val p = plano(
            treino(
                "A",
                // 145 passa do recorde de agachamento, que já é 144 (o "novo" que o treinador anotou).
                exercicio("Agachamento low bar", feita(serie("1x1 — RPE 9"), 145.0)),
                // 110 só iguala o recorde de supino: não é PR.
                exercicio("Paused bench press", feita(serie("1x1 — RPE 9"), 110.0)),
                // "High pull" não é barra fixa: 80kg aqui não bate o recorde de "Pull".
                exercicio("High pull", feita(serie("1x3— 80kg (Gravar)"), 80.0)),
                exercicio("Barra fixa (supinada)", feita(serie("3x6— RPE 6 (Gravar)"), 32.5)),
            ),
            treino(
                "D",
                exercicio("Paused Dead Sumo", feita(serie("3x3— RPE 5"), 150.0)),
                exercicio("Deadlift sumo", feita(serie("1x1 — RPE 9"), 205.0), naoFeita(serie("2x2 — diminuir 10% da série anterior"))),
                // Variação do mesmo levantamento, mais leve que a do treino A: fica a maior carga.
                exercicio("Tempo squat", feita(serie("6x1 — 75kg - RPE 6,5"), 75.0)),
            ),
        )
        assertEquals(
            listOf(
                PrBatido("Deadlift", 205.0, 200.0, "Deadlift sumo"),
                PrBatido("Squat", 145.0, 144.0, "Agachamento low bar"),
                PrBatido("Pull", 32.5, 30.0, "Barra fixa (supinada)"),
            ),
            prsBatidos(p),
        )
        val d = montarDados(p, sexta, emptyList(), emptyList())
        assertEquals(
            listOf(
                "* Deadlift: 205kg no Deadlift sumo (PR anterior: 200kg)",
                "* Squat: 145kg no Agachamento low bar (PR anterior: 144kg)",
                "* Pull: 32.5kg no Barra fixa (supinada) (PR anterior: 30kg)",
            ),
            textoDasProgressoes(d),
        )
        assertEquals(144.0, recordeAtual(prs[2])!!, 0.0)
        assertEquals(200.0, recordeAtual(prs[0])!!, 0.0)
    }

    @Test
    fun pedacos() {
        val d = semanaTranquila()
        // Peso: a pesagem mais recente, mesmo que o último dia não tenha pesagem.
        assertEquals("82kg", textoDoPeso(d.copy(checkins = listOf(checkin(dia = 5, peso = 82.0), checkin(dia = 6)))))
        assertEquals(
            "90%. Furei a dieta em 1 dia.",
            textoDaAderencia(d.copy(refeicoesFeitas = 18, checkins = listOf(checkin(dia = 6, furou = true)))),
        )
        // Sem refeições marcadas, vale o "furei a dieta": dia sem o check conta como seguido.
        assertEquals("100%.", textoDaAderencia(d.copy(refeicoesFeitas = 0, checkins = emptyList())))
        assertEquals(
            "${Math.round((d.dias - 2) * 100.0 / d.dias)}%. Furei a dieta em 2 dias.",
            textoDaAderencia(
                d.copy(refeicoesFeitas = 0, checkins = listOf(checkin(dia = 5, furou = true), checkin(dia = 6, furou = true))),
            ),
        )
        assertEquals("2 (variou de 0 a 4)", textoDaFome(d.copy(checkins = listOf(checkin(dia = 5, fome = 0), checkin(dia = 6, fome = 4), checkin(dia = 7, fome = 2)))))
        assertEquals("Cardio: 2 de 4 dias", textoDoCardio(d.copy(checkins = listOf(checkin(dia = 5, cardio = true), checkin(dia = 6, cardio = true), checkin(dia = 7)))))
        assertEquals(null, textoDoCardio(d.copy(checkins = emptyList())))
        assertEquals("Cardio: 1 dia", textoDoCardio(d.copy(cardioMetaDias = null, checkins = listOf(checkin(dia = 5, cardio = true)))))
    }

    @Test
    fun intestinoEAguaSoAparecemQuandoFogemDoCombinado() {
        val d = semanaTranquila()
        assertTrue(textoDasObservacoes(d).none { "Intestino" in it || "Água" in it })
        val comFalhas = d.copy(
            checkins = listOf(
                checkin(dia = 5, agua = 3000, intestino = true),
                checkin(dia = 6, agua = 3400, intestino = false),
            ),
            destaques = emptyList(),
        )
        assertEquals(
            listOf(
                "Intestino: fui ao banheiro em 1 de 2 dias.\nÁgua: média de 3.2 L por dia (meta de 4 L).",
                "Destaques dos treinos:\n- Cardio: 0 de 4 dias",
            ),
            textoDasObservacoes(comFalhas),
        )
    }

    @Test
    fun destaquesJuntamAsNotasDoMesmoExercicio() {
        val p = plano(
            treino("B", exercicio("Tempo squat", feita(serie("6x1 — 75kg - RPE 6,5"), 75.0, nota = "Joelho reclamou."))),
            treino("D", exercicio("Tempo squat", feita(serie("6x1 — 75kg - RPE 6,5"), 77.5, nota = "Melhor que no B."))),
        )
        assertEquals(listOf(DestaqueDoTreino("Tempo squat", "Joelho reclamou. Melhor que no B.")), destaquesDosTreinos(p))
    }

    @Test
    fun clipesELegendas() {
        val p = plano(
            treino(
                "A",
                exercicio("Agachamento low bar", feita(serie("8x1 — 90kg- RPE 7 (Gravar e me informar carga)"), 92.0, 7.0, "a.mp4")),
                exercicio("Tríceps pulley", naoFeita(serie("1x8 — RPE 5"))),
            ),
            treino("B", exercicio("High pull up", feita(serie("4x2- peso corporal (Gravar)"), null, null, "c.mp4"))),
        )
        val treinos = clipesPorTreino(p)
        assertEquals(listOf("Treino A", "Treino B"), treinos.map { it.treino.nome })
        val agachamento = treinos[0].clipes.single()
        assertEquals("8x1 · 92kg · RPE 7", legendaDoClipe(agachamento.serie, agachamento.registro))
        // Vídeo sem carga nem RPE registrados: a legenda usa o que estava prescrito.
        val highPull = treinos[1].clipes.single()
        assertEquals("4x2 · peso corporal", legendaDoClipe(highPull.serie, highPull.registro))
        // Só a carga respondida: o RPE vem do protocolo.
        assertEquals("8x1 · 95kg · RPE 7", legendaDoClipe(agachamento.serie, agachamento.registro.copy(cargaKg = 95.0, rpe = null)))
        val faixa = serie("3x4 a 6 — 100kg - RPE 7 a 8")
        val soCarga = agachamento.registro.copy(cargaKg = 102.5, rpe = null)
        assertEquals("3x4–6 · 102,5kg · RPE 7–8", legendaDoClipe(faixa, soCarga))
        assertTrue(clipesPorTreino(plano()).isEmpty())
    }
}
