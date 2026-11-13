package com.luiz.meuplano.parser

/**
 * Lê uma linha de série, por exemplo:
 *
 *   8x1 — 90kg-  RPE 7 (Gravar e me informar carga)
 *   4x4  — 70kg a  85kg - RPE 6,5 (Gravar e me informar carga)
 *   2x2 — diminuir 10% da série anterior
 *   4x2- peso corporal (Gravar) -Descruzar as pernas
 *   2x30 passos- 150kg (Gravar) usar cinturão
 *
 * O treinador digita essas linhas à mão, então os separadores e a ordem variam.
 * Cada informação é procurada por conta própria, e o que sobra vira observação.
 * O texto original é sempre guardado.
 */
internal object SerieParser {

    private const val NUM = """(\d+(?:[.,]\d+)?)"""
    private val I = RegexOption.IGNORE_CASE

    private val CABECALHO = Regex("""^\s*(\d+)\s*[x×]\s*(\d+)""", I)
    private val FAIXA_COM_A = Regex("""^\s*(?:a|à|até|ate)\s*(\d+)(?!\d)""", I)
    // "4-6" também é faixa, mas "3- 80kg" não: o número depois do hífen não pode ser carga.
    private val FAIXA_COM_HIFEN = Regex("""^\s?[-–]\s?(\d+)(?!\d|\s*(?:kg|%|[.,]\d))""", I)
    private val UNIDADE = Regex(
        """^\s*(passos|segundos|seg|minutos|min|metros|repetições|repeticoes|reps?|s|m)(?![\p{L}\d])""", I,
    )

    private val NAO_GRAVAR = Regex(
        """\bn[aã]o\s+(?:precisa\s+|necessita\s+|é\s+necess[aá]rio\s+)?gravar\b""", I,
    )
    private val GRAVAR = Regex("""\bgravar\b""", I)
    private val INFORMAR_CARGA = Regex("""\binformar\s+(?:a\s+)?carga\b""", I)
    private val PARENTESES_DE_GRAVACAO = Regex("""\([^()]*\b(?:gravar|informar)\b[^()]*\)""", I)
    private val GRAVACAO_SOLTA = Regex(
        """\bgravar\b(?:\s+e\s+me\s+informar\s+(?:a\s+)?carga\b)?|\b(?:me\s+)?informar\s+(?:a\s+)?carga\b""", I,
    )

    private val RPE = Regex(
        """\bRPE\s*:?\s*$NUM(?:\s*(?:a|até|ate)\s*$NUM|[-–]$NUM(?!\d|\s*kg))?""", I,
    )
    private val AJUSTE = Regex(
        """\b(diminuir|reduzir|baixar|tirar|aumentar|subir)\s+(?:em\s+)?$NUM\s*%""" +
            """(?:\s*(?:da|do|na|no)\s+(?:carga\s+da\s+)?(?:s[ée]rie|set)\s+anterior)?""",
        I,
    )
    private val PESO_CORPORAL = Regex("""\bpeso\s+(?:corporal|do\s+corpo)\b|\bbodyweight\b""", I)
    private val POR_LADO = Regex("""\b(?:de\s+)?(?:cada|por)\s+lado\b""", I)
    private val CARGA_DUPLA = Regex("""$NUM\s*(kgs?)?\s*(a|até|ate|[-–—])\s*$NUM\s*kgs?(?![\p{L}])""", I)
    private val CARGA = Regex("""$NUM\s*kgs?(?![\p{L}])""", I)

    private val PREFIXO_OBS = Regex("""^\s*obs\.?\s*:?\s*""", I)
    private val PARENTESES_VAZIOS = Regex("""\(\s*\)""")
    private val ESPACOS = Regex("""\s+""")
    private const val BORDAS = " \t-–—·,;:."

    /**
     * @param linha texto da linha, sem a observação que vem recuada logo abaixo
     * @param obsAbaixo essa observação recuada ("Obs: 30seg de descanso…"), se houver
     */
    fun parse(linha: String, obsAbaixo: String? = null): SeriePrescrita {
        val original = ESPACOS.replace(linha, " ").trim()
        var resto = original

        // 1. "8x1", "3x4 a 6", "2x30 passos"
        var series: Int? = null
        var repsMin: Int? = null
        var repsMax: Int? = null
        var unidade: String? = null
        val cabecalho = CABECALHO.find(resto)
        if (cabecalho != null) {
            series = cabecalho.groupValues[1].toIntOrNull()
            repsMin = cabecalho.groupValues[2].toIntOrNull()
            repsMax = repsMin
            resto = resto.substring(cabecalho.range.last + 1)

            val faixa = FAIXA_COM_A.find(resto) ?: FAIXA_COM_HIFEN.find(resto)
            val fim = faixa?.groupValues?.get(1)?.toIntOrNull()
            if (faixa != null && fim != null && repsMin != null && fim > repsMin) {
                repsMax = fim
                resto = resto.substring(faixa.range.last + 1)
            }
            UNIDADE.find(resto)?.let {
                unidade = normalizarUnidade(it.groupValues[1])
                resto = resto.substring(it.range.last + 1)
            }
        }
        val interpretada = series != null && repsMin != null

        // 2. Gravar / informar carga
        val gravar = GRAVAR.containsMatchIn(resto) && !NAO_GRAVAR.containsMatchIn(resto)
        val informarCarga = INFORMAR_CARGA.containsMatchIn(resto)
        if (gravar || informarCarga) {
            resto = PARENTESES_DE_GRAVACAO.replace(resto, " ")
            resto = GRAVACAO_SOLTA.replace(resto, " ")
        }

        // 3. RPE
        var rpeMin: Double? = null
        var rpeMax: Double? = null
        RPE.find(resto)?.let { m ->
            rpeMin = m.groupValues[1].numero()
            rpeMax = (m.groupValues[2].ifEmpty { m.groupValues[3] }).numero() ?: rpeMin
            resto = resto.removeRange(m.range).inserirEspaco(m.range.first)
        }

        // 4. "diminuir 10% da série anterior"
        var ajuste: Double? = null
        AJUSTE.find(resto)?.let { m ->
            val valor = m.groupValues[2].numero()
            if (valor != null) {
                val sobe = m.groupValues[1].lowercase() in setOf("aumentar", "subir")
                ajuste = if (sobe) valor else -valor
                resto = resto.removeRange(m.range).inserirEspaco(m.range.first)
            }
        }

        // 5. Peso corporal
        var pesoCorporal = false
        PESO_CORPORAL.find(resto)?.let { m ->
            pesoCorporal = true
            resto = resto.removeRange(m.range).inserirEspaco(m.range.first)
        }

        // 6. Carga: "90kg", "70kg a 85kg", "10kg-10kg"
        var cargaMin: Double? = null
        var cargaMax: Double? = null
        var porLado = false
        val dupla = CARGA_DUPLA.find(resto)?.takeIf { m ->
            // "70 a 85kg" vale; "7 - 90kg" não, porque o 7 pode ser outra coisa.
            m.groupValues[2].isNotEmpty() || m.groupValues[3].lowercase() in setOf("a", "até", "ate")
        }
        if (dupla != null) {
            val a = dupla.groupValues[1].numero()
            val b = dupla.groupValues[4].numero()
            val comHifen = dupla.groupValues[3] in setOf("-", "–", "—")
            if (a != null && b != null) {
                if (comHifen && a == b) {
                    porLado = true
                    cargaMin = a
                    cargaMax = a
                } else {
                    cargaMin = minOf(a, b)
                    cargaMax = maxOf(a, b)
                }
                resto = resto.removeRange(dupla.range).inserirEspaco(dupla.range.first)
            }
        } else {
            CARGA.find(resto)?.let { m ->
                cargaMin = m.groupValues[1].numero()
                cargaMax = cargaMin
                resto = resto.removeRange(m.range).inserirEspaco(m.range.first)
            }
        }
        POR_LADO.find(resto)?.let { m ->
            if (cargaMin != null) {
                porLado = true
                resto = resto.removeRange(m.range).inserirEspaco(m.range.first)
            }
        }

        // 7. O que sobrou é observação
        val sobra = if (interpretada) limpar(resto) else null
        val abaixo = obsAbaixo?.let { PREFIXO_OBS.replace(ESPACOS.replace(it, " "), "").trim() }
        val observacao = listOfNotNull(sobra, abaixo).filter { it.isNotEmpty() }
            .joinToString("\n").ifEmpty { null }

        return SeriePrescrita(
            textoOriginal = original,
            series = series,
            repsMin = repsMin,
            repsMax = repsMax,
            unidade = unidade,
            cargaMinKg = cargaMin,
            cargaMaxKg = cargaMax,
            cargaPorLado = porLado,
            pesoCorporal = pesoCorporal,
            ajustePercentual = ajuste,
            rpeMin = rpeMin,
            rpeMax = rpeMax,
            gravar = gravar,
            informarCarga = informarCarga,
            observacao = observacao,
            interpretada = interpretada,
        )
    }

    /** As séries que o treinador deixou fora da lista e a observação que sobrou. */
    data class SeriesDaObservacao(val series: List<SeriePrescrita>, val observacao: String?)

    /**
     * Exercício sem nenhuma série na lista: às vezes o treinador escreve a série num
     * parágrafo ("1x1- peso corporal (Gravar) pode testar mais de uma vez"). As linhas
     * da observação que começam com "NxM" viram séries; o resto continua observação.
     * Se nenhuma linha tiver esse formato, cria uma série livre (para dar para registrar
     * e gravar), que pede vídeo se a observação falar em gravar.
     */
    fun seriesDaObservacao(observacao: String?): SeriesDaObservacao {
        val linhas = observacao?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val series = mutableListOf<SeriePrescrita>()
        val sobra = mutableListOf<String>()
        for (linha in linhas) {
            if (CABECALHO.containsMatchIn(linha)) series += parse(linha) else sobra += linha
        }
        if (series.isNotEmpty()) return SeriesDaObservacao(series, sobra.joinToString("\n").ifEmpty { null })
        val texto = linhas.joinToString(" ")
        val livre = parse("Série livre").copy(
            gravar = GRAVAR.containsMatchIn(texto) && !NAO_GRAVAR.containsMatchIn(texto),
        )
        return SeriesDaObservacao(listOf(livre), observacao)
    }

    private fun limpar(texto: String): String {
        var t = PARENTESES_VAZIOS.replace(texto, " ")
        t = ESPACOS.replace(t, " ").trim { it in BORDAS }
        // Sobraram só separadores entre as partes retiradas ("- -").
        if (t.none { it.isLetterOrDigit() }) return ""
        if (t.startsWith("(") && t.endsWith(")") && t.indexOf(')') == t.length - 1) {
            t = t.substring(1, t.length - 1).trim()
        }
        return t
    }

    /** Repetições são o padrão, então "reps" vira nulo. */
    private fun normalizarUnidade(u: String): String? = when (u.lowercase()) {
        "segundos", "seg", "s" -> "seg"
        "minutos", "min" -> "min"
        "metros", "m" -> "m"
        "passos" -> "passos"
        else -> null
    }

    private fun String.numero(): Double? = replace(',', '.').toDoubleOrNull()

    private fun String.inserirEspaco(posicao: Int): String =
        substring(0, posicao) + " " + substring(posicao)
}
