package com.luiz.meuplano.feedback

import androidx.compose.runtime.Immutable
import com.luiz.meuplano.data.CheckinDiarioEntity
import com.luiz.meuplano.data.ExercicioEntity
import com.luiz.meuplano.data.PlanoCompleto
import com.luiz.meuplano.data.PrEntity
import com.luiz.meuplano.data.RefeicaoFeitaEntity
import com.luiz.meuplano.data.SerieFeitaEntity
import com.luiz.meuplano.data.SeriePrescritaEntity
import com.luiz.meuplano.data.TreinoEntity
import com.luiz.meuplano.parser.PlanoParser
import com.luiz.meuplano.ui.resumo
import com.luiz.meuplano.ui.semZeros
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/*
 * Feedback semanal: junta o que foi registrado na semana e monta o texto no
 * modelo que o treinador pede. Nada aqui usa o Android, então tudo é testável
 * (o @Immutable é só uma marca para o Compose redesenhar menos).
 *
 * O texto segue o jeito que o Luiz escreve o feedback dele: curto. Peso é um número,
 * aderência é uma porcentagem, e em "Progressões de Carga" só entra PR batido.
 */

/** Tudo que entra no texto do feedback. */
@Immutable
data class DadosDaSemana(
    val inicio: LocalDate,
    /** Último dia considerado: hoje, ou o 7º dia do plano se já passou. */
    val fim: LocalDate,
    /** Check-ins dentro do período, em ordem de data. */
    val checkins: List<CheckinDiarioEntity>,
    val refeicoesPorDia: Int,
    val refeicoesFeitas: Int,
    /** Recordes da tabela de PRs que foram superados nesta semana. */
    val prsBatidos: List<PrBatido>,
    /** O que foi anotado nos exercícios, na ordem do plano. */
    val destaques: List<DestaqueDoTreino>,
    val cardioMetaDias: Int?,
    val aguaMetaLitros: Double?,
) {
    val dias: Int get() = ChronoUnit.DAYS.between(inicio, fim).toInt() + 1
}

/**
 * @param pr nome do recorde como está na tabela do treinador ("Squat")
 * @param exercicio exercício do plano em que a carga foi feita ("Agachamento low bar")
 */
data class PrBatido(val pr: String, val cargaKg: Double, val anteriorKg: Double, val exercicio: String)

data class DestaqueDoTreino(val exercicio: String, val nota: String)

/** Um vídeo gravado, com o que a legenda precisa. */
@Immutable
data class ClipeDoTreino(
    val exercicio: ExercicioEntity,
    val serie: SeriePrescritaEntity,
    val registro: SerieFeitaEntity,
    val videoArquivo: String,
)

@Immutable
data class TreinoComClipes(val treino: TreinoEntity, val clipes: List<ClipeDoTreino>)

/** O nome que aparece na linha pequena do topo das telas. */
const val NOME_DO_APP = "Meu Plano"

const val CABECALHO_DO_FEEDBACK = "FEEDBACK SEMANAL 🏋🏻‍♂️"

/**
 * A semana do plano: começa no dia em que o treinador gerou o arquivo e vai até
 * hoje, com no máximo 7 dias. Sem data no plano, vale os últimos 7 dias.
 */
fun periodoDaSemana(geradoEm: String?, hoje: LocalDate): Pair<LocalDate, LocalDate> {
    val doPlano = geradoEm?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val inicio = if (doPlano == null || doPlano.isAfter(hoje)) hoje.minusDays(6) else doPlano
    val fim = minOf(hoje, inicio.plusDays(6))
    return inicio to fim
}

/**
 * @param checkins check-ins que incluam o período (os de fora são ignorados)
 * @param refeicoesFeitas refeições marcadas que incluam o período
 */
fun montarDados(
    plano: PlanoCompleto,
    hoje: LocalDate,
    checkins: List<CheckinDiarioEntity>,
    refeicoesFeitas: List<RefeicaoFeitaEntity>,
): DadosDaSemana {
    val (inicio, fim) = periodoDaSemana(plano.plano.geradoEm, hoje)
    // Datas ISO (2026-10-09) ordenam certo como texto.
    val de = inicio.toString()
    val ate = fim.toString()

    val refeicoesDoPlano = plano.refeicoes.map { it.refeicao.id }.toSet()
    val feitas = refeicoesFeitas.count { it.refeicaoId in refeicoesDoPlano && it.data in de..ate }

    return DadosDaSemana(
        inicio = inicio,
        fim = fim,
        checkins = checkins.filter { it.data in de..ate }.sortedBy { it.data },
        refeicoesPorDia = plano.refeicoes.size,
        refeicoesFeitas = feitas,
        prsBatidos = prsBatidos(plano),
        destaques = destaquesDosTreinos(plano),
        cardioMetaDias = plano.plano.cardioDiasSemana,
        aguaMetaLitros = plano.plano.metaAguaLitros,
    )
}

/** Os vídeos gravados de cada treino, na ordem do plano. Treinos sem vídeo ficam de fora. */
fun clipesPorTreino(plano: PlanoCompleto): List<TreinoComClipes> =
    plano.treinos.mapNotNull { treino ->
        val clipes = treino.exercicios.flatMap { exercicio ->
            exercicio.series.mapNotNull { linha ->
                val registro = linha.registro
                val video = registro?.videoArquivo
                if (registro == null || video == null) null
                else ClipeDoTreino(exercicio.exercicio, linha.serie, registro, video)
            }
        }
        if (clipes.isEmpty()) null else TreinoComClipes(treino.treino, clipes)
    }

// ---------- PRs ----------

/**
 * Um levantamento com recorde na tabela do treinador.
 *
 * @param noPr palavras que identificam o recorde na tabela ("Squat", "Bench press")
 * @param noExercicio palavras que identificam os exercícios do plano que contam para ele
 */
private class Levantamento(val noPr: List<String>, val noExercicio: List<String>)

/*
 * Tudo em minúsculas e sem acento. "Pull" é a barra fixa com carga: "high pull" é outro
 * movimento e não conta, por isso os exercícios pedem "pull up" ou "barra fixa".
 */
private val LEVANTAMENTOS = listOf(
    Levantamento(listOf("squat", "agachamento"), listOf("squat", "agachamento")),
    Levantamento(listOf("bench", "supino"), listOf("bench", "supino")),
    Levantamento(listOf("deadlift", "terra"), listOf("deadlift", "dead ", "terra")),
    Levantamento(listOf("overhead", "ohp", "desenvolvimento"), listOf("overhead", "ohp", "desenvolvimento")),
    Levantamento(listOf("dip", "paralela"), listOf("dip", "paralela")),
    Levantamento(listOf("pull", "barra fixa", "chin"), listOf("pull up", "pull-up", "pullup", "barra fixa", "chin")),
)

private val QUILOS = Regex("""(\d+(?:[.,]\d+)?)\s*kg""", RegexOption.IGNORE_CASE)

/**
 * O recorde que vale para comparar: o maior peso que aparece no texto do treinador.
 * "140kg (144kg novo)" -> 144, porque o 144 ele já anotou.
 */
internal fun recordeAtual(pr: PrEntity): Double? =
    QUILOS.findAll(pr.texto).mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }.maxOrNull()
        ?: pr.cargaKg

/**
 * Recordes superados nesta semana: para cada linha da tabela de PRs, a maior carga
 * registrada nos exercícios daquele levantamento, se passou do recorde.
 *
 * É um lembrete, não um juiz: variações (pausado, tempo, sumo) contam junto, e cabe
 * a quem treinou confirmar no texto se vale como PR.
 */
fun prsBatidos(plano: PlanoCompleto): List<PrBatido> {
    val exercicios = plano.treinos.flatMap { it.exercicios }
    return plano.prs.mapNotNull { pr ->
        val recorde = recordeAtual(pr) ?: return@mapNotNull null
        val nomeDoPr = PlanoParser.semAcento(pr.exercicio)
        val levantamento = LEVANTAMENTOS.firstOrNull { l -> l.noPr.any { it in nomeDoPr } }
            ?: return@mapNotNull null

        val melhor = exercicios
            .filter { e ->
                // O espaço no fim deixa "dead " casar com "paused dead" no final do nome.
                val nome = PlanoParser.semAcento(e.exercicio.nome) + " "
                levantamento.noExercicio.any { it in nome }
            }
            .flatMap { e -> e.series.mapNotNull { linha -> linha.registro?.cargaKg?.let { it to e.exercicio.nome } } }
            .maxByOrNull { it.first }
            ?: return@mapNotNull null

        if (melhor.first > recorde) PrBatido(pr.exercicio, melhor.first, recorde, melhor.second) else null
    }
}

/** As anotações feitas nas séries, juntas por exercício e na ordem do plano. */
fun destaquesDosTreinos(plano: PlanoCompleto): List<DestaqueDoTreino> {
    // O mesmo exercício pode estar em mais de um treino: as notas dele ficam em uma linha só.
    val porExercicio = linkedMapOf<String, Pair<String, MutableList<String>>>()
    for (treino in plano.treinos) {
        for (exercicio in treino.exercicios) {
            for (linha in exercicio.series) {
                val nota = linha.registro?.anotacao?.trim().orEmpty()
                if (nota.isEmpty()) continue
                val entrada = porExercicio.getOrPut(exercicio.exercicio.chave) {
                    exercicio.exercicio.nome to mutableListOf()
                }
                if (nota !in entrada.second) entrada.second += nota
            }
        }
    }
    return porExercicio.values.map { (nome, notas) -> DestaqueDoTreino(nome, notas.joinToString(" ")) }
}

// ---------- texto ----------

/** O feedback inteiro, no modelo do treinador. Campos sem dado ficam em branco para completar à mão. */
fun textoDoFeedback(d: DadosDaSemana): String = buildString {
    appendLine(CABECALHO_DO_FEEDBACK)
    appendLine()
    appendLine("Peso: ${textoDoPeso(d)}".trimEnd())
    appendLine("Fotos: Abaixo")
    appendLine()
    appendLine("✅ Aderência à dieta: ${textoDaAderencia(d)}".trimEnd())
    appendLine()
    appendLine("📈 Progressões de Carga:")
    textoDasProgressoes(d).forEach { appendLine(it) }
    appendLine()
    appendLine("🍽️ Nível de fome: ${textoDaFome(d)}".trimEnd())
    appendLine()
    appendLine("📝 Observações gerais e como me senti:")
    for (paragrafo in textoDasObservacoes(d)) {
        appendLine()
        appendLine(paragrafo)
    }
}.trimEnd()

/** A pesagem mais recente da semana: "81.5kg". */
internal fun textoDoPeso(d: DadosDaSemana): String {
    val peso = d.checkins.lastOrNull { it.pesoKg != null }?.pesoKg ?: return ""
    return "${peso.comPonto()}kg"
}

/**
 * "100%." e, se houve, em quantos dias a dieta furou.
 *
 * Com refeições marcadas na semana, a porcentagem é das refeições feitas. Sem nenhuma
 * marcada, vale o "furei a dieta": todo dia sem esse check conta como dieta seguida.
 */
internal fun textoDaAderencia(d: DadosDaSemana): String {
    val furos = d.checkins.count { it.furouDieta }
    val dias = d.dias.coerceAtLeast(1)
    val total = d.refeicoesPorDia * d.dias
    val porcento = if (d.refeicoesFeitas > 0 && total > 0) {
        (d.refeicoesFeitas * 100.0 / total).roundToInt().coerceAtMost(100)
    } else {
        ((dias - furos).coerceAtLeast(0) * 100.0 / dias).roundToInt()
    }
    val furou = when (furos) {
        0 -> ""
        1 -> " Furei a dieta em 1 dia."
        else -> " Furei a dieta em $furos dias."
    }
    return "$porcento%.$furou"
}

/** Só PR batido entra aqui. Sem nenhum, "* Nenhuma." */
internal fun textoDasProgressoes(d: DadosDaSemana): List<String> {
    if (d.prsBatidos.isEmpty()) return listOf("* Nenhuma.")
    return d.prsBatidos.map {
        "* ${it.pr}: ${it.cargaKg.comPonto()}kg no ${it.exercicio} (PR anterior: ${it.anteriorKg.comPonto()}kg)"
    }
}

/** Um número só. Se variou na semana, a média arredondada e de quanto a quanto foi. */
internal fun textoDaFome(d: DadosDaSemana): String {
    val niveis = d.checkins.mapNotNull { it.fome }
    if (niveis.isEmpty()) return ""
    val menor = niveis.min()
    val maior = niveis.max()
    if (menor == maior) return "$menor"
    return "${niveis.average().roundToInt()} (variou de $menor a $maior)"
}

/**
 * Os parágrafos das observações, na ordem: o que foi anotado em cada dia, os avisos
 * (intestino e água, só quando fugiram do combinado) e os destaques dos treinos.
 */
internal fun textoDasObservacoes(d: DadosDaSemana): List<String> {
    val paragrafos = mutableListOf<String>()

    val notasDosDias = d.checkins.mapNotNull { it.anotacao?.trim()?.ifEmpty { null } }
    if (notasDosDias.isNotEmpty()) paragrafos += notasDosDias.joinToString("\n")

    val avisos = mutableListOf<String>()
    val respondidos = d.checkins.mapNotNull { it.intestino }
    val foi = respondidos.count { it }
    // O treinador pede 1x por dia: só vira assunto quando algum dia falhou.
    if (foi < respondidos.size) avisos += "Intestino: fui ao banheiro em $foi de ${respondidos.size} dias."
    val aguas = d.checkins.map { it.aguaMl }.filter { it > 0 }
    val metaDeAgua = d.aguaMetaLitros
    if (aguas.isNotEmpty() && metaDeAgua != null) {
        val media = umaCasa(aguas.average() / 1000)
        if (media < metaDeAgua) avisos += "Água: média de ${media.comPonto()} L por dia (meta de ${metaDeAgua.comPonto()} L)."
    }
    if (avisos.isNotEmpty()) paragrafos += avisos.joinToString("\n")

    val destaques = d.destaques.map { "- ${it.exercicio}: ${it.nota}" }.toMutableList()
    textoDoCardio(d)?.let { destaques += "- $it" }
    if (destaques.isNotEmpty()) paragrafos += "Destaques dos treinos:\n" + destaques.joinToString("\n")

    return paragrafos
}

/** "Cardio concluído os 4 dias" ou "Cardio: 2 de 4 dias". Nulo quando não há o que dizer. */
internal fun textoDoCardio(d: DadosDaSemana): String? {
    val feitos = d.checkins.count { it.cardioFeito }
    val meta = d.cardioMetaDias
    return when {
        meta != null && feitos >= meta -> "Cardio concluído os $meta dias"
        // Sem nenhum check-in na semana, zero cardio é só falta de registro.
        meta != null && d.checkins.isNotEmpty() -> "Cardio: $feitos de $meta dias"
        meta == null && feitos > 0 -> if (feitos == 1) "Cardio: 1 dia" else "Cardio: $feitos dias"
        else -> null
    }
}

// ---------- pedaços de texto reaproveitados ----------

/** A segunda linha da legenda do vídeo: "8x1 · 92kg · RPE 7" (o que foi feito, não o prescrito). */
fun legendaDoClipe(serie: SeriePrescritaEntity, registro: SerieFeitaEntity): String {
    val carga = registro.cargaKg
    // Sem RPE respondido, vale o do protocolo ("RPE 7" ou "RPE 7–8").
    val rpe = registro.rpe?.semZeros() ?: rpePrescrito(serie)
    if (carga == null && registro.rpe == null) return serie.resumo()
    val partes = mutableListOf(seriesERepeticoes(serie))
    when {
        carga != null && serie.pesoCorporal -> partes += "peso corporal + ${carga.semZeros()}kg"
        carga != null -> partes += "${carga.semZeros()}kg"
        serie.pesoCorporal -> partes += "peso corporal"
    }
    if (rpe != null) partes += "RPE $rpe"
    return partes.joinToString(" · ")
}

/** O RPE que o treinador pediu na série: "7", "7–8", ou nulo se não pediu. */
private fun rpePrescrito(serie: SeriePrescritaEntity): String? {
    val min = serie.rpeMin ?: return null
    val max = serie.rpeMax
    return if (max != null && max != min) "${min.semZeros()}–${max.semZeros()}" else min.semZeros()
}

/** "8x1", "3x4–6", "2x30 passos". Linha fora do padrão volta como o treinador escreveu. */
private fun seriesERepeticoes(serie: SeriePrescritaEntity): String {
    val s = serie.series
    val min = serie.repsMin
    if (!serie.interpretada || s == null || min == null) return serie.textoOriginal
    val max = serie.repsMax
    val reps = if (max != null && max != min) "$min–$max" else "$min"
    return "${s}x$reps" + (serie.unidade?.let { " $it" } ?: "")
}

/** Número como o Luiz escreve no feedback: 81.5 -> "81.5", 145.0 -> "145". */
internal fun Double.comPonto(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun umaCasa(valor: Double): Double = Math.round(valor * 10) / 10.0
