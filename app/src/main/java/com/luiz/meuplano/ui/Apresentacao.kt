package com.luiz.meuplano.ui

import com.luiz.meuplano.data.ExercicioComSeries
import com.luiz.meuplano.data.PrEntity
import com.luiz.meuplano.data.SerieComRegistro
import com.luiz.meuplano.data.TreinoComExercicios
import com.luiz.meuplano.data.TrocaDoDiaEntity
import com.luiz.meuplano.parser.PlanoParser
import com.luiz.meuplano.trocas.BancoDeAlimentos
import com.luiz.meuplano.trocas.Troca
import com.luiz.meuplano.trocas.calcularTroca
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * Como os dados do plano viram texto nas telas do novo design.
 * Nada aqui depende do Android, então dá para testar fora dele.
 */

// ---------- treino ----------

/**
 * O exercício está feito quando todas as séries foram registradas e tudo que o treinador
 * pediu foi entregue: vídeo nas séries marcadas com "Gravar" e carga nas marcadas com
 * "informar carga". Só aí o "agora" passa para o próximo exercício.
 */
fun ExercicioComSeries.feito(): Boolean {
    if (series.isEmpty() || series.any { it.registro == null }) return false
    return series.all { linha ->
        val registro = linha.registro
        registro != null &&
            (!linha.serie.gravar || registro.videoArquivo != null) &&
            (!linha.serie.informarCarga || registro.cargaKg != null)
    }
}

/** "1/6": exercícios feitos sobre o total do treino. */
fun TreinoComExercicios.progresso(): String = "${exercicios.count { it.feito() }}/${exercicios.size}"

/** Posição do primeiro exercício ainda não feito (o "agora"), ou -1 se o treino acabou. */
fun TreinoComExercicios.exercicioAtual(): Int = exercicios.indexOfFirst { !it.feito() }

/** O exercício fechado em uma linha: "2×6 · @9" ou, com várias séries, "1×6 · 1×3 · 3×4–6". */
fun ExercicioComSeries.resumoCurto(): String {
    val unica = series.singleOrNull()?.serie
    if (unica != null) {
        val rpe = unica.rpeMin?.let { " · @${it.semZeros()}" }.orEmpty()
        return seriesVezesReps(unica.series, unica.repsMin, unica.repsMax) + rpe
    }
    return series.take(3).joinToString(" · ") { seriesVezesReps(it.serie.series, it.serie.repsMin, it.serie.repsMax) }
}

private fun seriesVezesReps(series: Int?, min: Int?, max: Int?): String {
    if (series == null || min == null) return "—"
    return "$series×" + if (max != null && max != min) "$min–$max" else "$min"
}

/** As quatro células de uma série na tabela do cartão. */
data class CelulasDaSerie(
    val series: String,
    val reps: String,
    /** "90", "70–85", "PC", "−10%" ou "—" */
    val carga: String,
    /** Verdadeiro quando [carga] é um peso em kg (para mostrar o "kg" ao lado). */
    val cargaEmKg: Boolean,
    /** Verdadeiro quando a carga é a que você registrou, e não a do plano. */
    val cargaRegistrada: Boolean,
    val rpe: String,
)

fun SerieComRegistro.celulas(): CelulasDaSerie {
    val s = serie
    val feita = registro
    val min = s.repsMin
    val max = s.repsMax
    val reps = when {
        min == null -> "—"
        max != null && max != min -> "$min–$max"
        else -> "$min"
    } + (s.unidade?.let { " $it" } ?: "")

    val cargaFeita = feita?.cargaKg
    val cMin = s.cargaMinKg
    val cMax = s.cargaMaxKg
    val ajuste = s.ajustePercentual
    val (carga, emKg) = when {
        cargaFeita != null -> cargaFeita.semZeros() to true
        cMin != null && cMax != null && cMax != cMin -> "${cMin.semZeros()}–${cMax.semZeros()}" to true
        cMin != null -> cMin.semZeros() to true
        s.pesoCorporal -> "PC" to false
        ajuste != null -> ((if (ajuste < 0) "−" else "+") + "${kotlin.math.abs(ajuste).semZeros()}%") to false
        else -> "—" to false
    }

    val rpe = feita?.rpe ?: s.rpeMin
    return CelulasDaSerie(
        series = s.series?.let { "$it×" } ?: "—",
        reps = reps,
        carga = carga,
        cargaEmKg = emKg,
        cargaRegistrada = cargaFeita != null,
        rpe = rpe?.semZeros() ?: "—",
    )
}

/**
 * A série na pílula da câmera: o que vai de carga e o RPE, separados porque o RPE
 * aparece em laranja. Ex.: ("90 kg", "RPE 7"), ("PC", null), ("8×1", "RPE 7").
 */
fun SerieComRegistro.resumoParaCamera(): Pair<String, String?> {
    val celulas = celulas()
    val principal = when {
        celulas.cargaEmKg -> "${celulas.carga} kg"
        celulas.carga != "—" -> celulas.carga
        // Sem carga definida ainda: mostra séries e repetições.
        else -> celulas.series + celulas.reps
    }
    return principal to celulas.rpe.takeIf { it != "—" }?.let { "RPE $it" }
}

// ---------- dieta ----------

/**
 * A troca guardada no dia, refeita com o banco de alimentos atual.
 * Nulo se algum dos alimentos não está mais no banco: aí o item volta a aparecer como na dieta.
 */
fun TrocaDoDiaEntity.calcular(): Troca? {
    val de = BancoDeAlimentos.porNome(origem) ?: return null
    val para1 = BancoDeAlimentos.porNome(destino1) ?: return null
    val para2 = if (destino2 != null) BancoDeAlimentos.porNome(destino2) ?: return null else null
    return calcularTroca(de, quantidade, para1, para2, proporcao)
}

private val QUANTIDADE = Regex(
    """^(\d+(?:[.,]\d+)?)(?:\s*(?:a|-|–)\s*(\d+(?:[.,]\d+)?))?\s*""" +
        """(kg|g|ml|l|fatias?|unidades?|scoops?|colheres?(?:\s+de\s+(?:sopa|ch[áa]|sobremesa))?)?""" +
        """(?![\p{L}\d])\s*(?:de\s+)?(.+)$""",
    RegexOption.IGNORE_CASE,
)

/**
 * Separa o item da refeição em nome e quantidade, como no design:
 * "30g whey" -> ("Whey", "30 g"); "2 fatias de pão de forma" -> ("Pão de forma", "2 fatias").
 * Sem número no começo, a quantidade vem nula.
 */
fun separarQuantidade(texto: String): Pair<String, String?> {
    val m = QUANTIDADE.find(texto.trim()) ?: return texto.trim() to null
    val (de, ate, unidade, nome) = m.destructured
    val numero = if (ate.isEmpty()) de else "$de–$ate"
    val quantidade = if (unidade.isEmpty()) numero else "$numero ${unidade.lowercase()}"
    return nome.trim().replaceFirstChar { it.uppercase() } to quantidade
}

// Compiladas uma vez: estas funções rodam a cada item desenhado na tela.
private val OPCAO = Regex("""^Op[cç][aã]o\s+(\w+)\s*\((.+)\)\s*$""", RegexOption.IGNORE_CASE)
private val ORDINAL = Regex("""^(\d+\s*[ªºa])\s+(.+)$""")
private val ENTRE_PARENTESES = Regex("""\(([^()]+)\)""")
private val PALAVRA_NOVO = Regex("""\bnovo\b""", RegexOption.IGNORE_CASE)

/** "Opção A (Whey + Pão de forma)" -> "A · Whey + Pão de forma". Fora desse formato, volta igual. */
fun rotuloDaOpcao(nome: String?): String {
    if (nome == null) return "Única"
    val m = OPCAO.find(nome.trim()) ?: return nome.trim()
    return "${m.groupValues[1].uppercase()} · ${m.groupValues[2].trim()}"
}

/** "🌅 1ª Refeição" -> ("1ª", "Refeição"). Sem ordinal no título, usa a posição na lista. */
fun tituloDaRefeicao(titulo: String, posicao: Int): Pair<String, String> {
    val limpo = semSimbolosNoInicio(titulo)
    val m = ORDINAL.find(limpo)
    return if (m != null) m.groupValues[1].replace(" ", "") to m.groupValues[2] else "${posicao + 1}ª" to limpo
}

private val DIA_ABREVIADO = DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("pt-BR"))
private val DIA_E_MES = DateTimeFormatter.ofPattern("dd/MM")

/** "SEX · 09/10" */
fun diaEmDestaque(dia: LocalDate): String =
    dia.format(DIA_ABREVIADO).trimEnd('.').uppercase() + " · " + dia.format(DIA_E_MES)

/** "Hoje", "Ontem" ou "Há 3 dias" */
fun diaRelativo(dia: LocalDate, hoje: LocalDate): String = when (val dias = ChronoUnit.DAYS.between(dia, hoje)) {
    0L -> "Hoje"
    1L -> "Ontem"
    else -> "Há $dias dias"
}

/** "05/10 – 09/10" */
fun periodoCurto(inicio: LocalDate, fim: LocalDate): String = "${inicio.format(DIA_E_MES)} – ${fim.format(DIA_E_MES)}"

// ---------- info ----------

/** "140kg (144kg novo)" -> ["NOVO 144kg"]. Cada parêntese vira uma marca. */
fun notasDoPr(texto: String): List<String> =
    ENTRE_PARENTESES.findAll(texto).map { m ->
        val dentro = m.groupValues[1].trim()
        val semNovo = dentro.replace(PALAVRA_NOVO, "").trim()
        if (semNovo.length != dentro.length) "NOVO $semNovo" else dentro
    }.toList()

/** Agachamento, supino e terra, nessa ordem, e a soma. Nulo se faltar algum dos três. */
data class TotalSbd(val agachamento: Double, val supino: Double, val terra: Double) {
    val total: Double get() = agachamento + supino + terra
}

fun totalSbd(prs: List<PrEntity>): TotalSbd? {
    fun achar(vararg nomes: String): Double? = prs.firstOrNull { pr ->
        val chave = PlanoParser.semAcento(pr.exercicio)
        nomes.any { it in chave }
    }?.cargaKg
    val s = achar("squat", "agachamento") ?: return null
    val b = achar("bench", "supino") ?: return null
    val d = achar("deadlift", "terra") ?: return null
    return TotalSbd(s, b, d)
}

/** Uma linha de texto do plano: o que vem antes do travessão é o título, o resto é o detalhe. */
data class LinhaDeInfo(val titulo: String, val detalhe: String?)

/**
 * As linhas de um bloco de texto, prontas para a lista da tela Info.
 * "• Aniracetam 250mg — tomar dia sim e dia não" -> ("Aniracetam 250mg", "tomar dia sim e dia não").
 * Linhas recuadas (começam com dois espaços) juntam-se ao detalhe da linha de cima.
 */
fun linhasDeInfo(texto: String): List<LinhaDeInfo> {
    val linhas = mutableListOf<LinhaDeInfo>()
    for (bruta in texto.lines()) {
        if (bruta.isBlank()) continue
        if (bruta.startsWith("  ") && linhas.isNotEmpty()) {
            val anterior = linhas.removeAt(linhas.size - 1)
            linhas += anterior.copy(detalhe = listOfNotNull(anterior.detalhe, bruta.trim()).joinToString("\n"))
            continue
        }
        val limpa = bruta.trim().removePrefix("•").trim()
        val corte = limpa.indexOf(" — ")
        // Só separa quando o começo é curto, como um nome ou uma regra; frase longa fica inteira.
        linhas += if (corte in 1..40) {
            LinhaDeInfo(limpa.substring(0, corte).trim(), limpa.substring(corte + 3).trim())
        } else {
            LinhaDeInfo(limpa, null)
        }
    }
    return linhas
}

/** Tira o emoji que o Notion põe antes dos títulos ("💪 Treino A"). */
fun semSimbolosNoInicio(texto: String): String = texto.dropWhile { !it.isLetterOrDigit() }.trim().ifEmpty { texto.trim() }
