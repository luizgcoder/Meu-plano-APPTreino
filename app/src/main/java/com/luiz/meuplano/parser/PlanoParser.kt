package com.luiz.meuplano.parser

import java.text.Normalizer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Lê o HTML que o treinador exporta do Notion.
 *
 * A estrutura do arquivo é fixa e é nela que o parser se apoia:
 *
 *   h1  seção grande (Dieta, Sugestão de Treino, Diretrizes…)
 *   h2  "Refeições", "Treino A", "Cardio" ou um bloco de texto
 *   h3  uma refeição (dentro de "Refeições")
 *   details.toggle  uma opção da refeição
 *   p > strong      um exercício (dentro de "Treino X")
 *   ul              itens da refeição ou séries do exercício
 *
 * O que não se encaixa em nada vira bloco de texto ou aviso, nunca some.
 */
object PlanoParser {

    fun parse(html: String): Plano = parse(Jsoup.parse(html))

    fun parse(doc: Document): Plano = Leitor(doc).ler()

    private enum class Modo { INFO, REFEICOES, TREINO }

    private class RefeicaoEmMontagem(val titulo: String) {
        val opcoes = mutableListOf<OpcaoRefeicao>()
    }

    private class TreinoEmMontagem(val nome: String, val letra: String?) {
        val exercicios = mutableListOf<ExercicioEmMontagem>()
    }

    private class ExercicioEmMontagem(
        val nomeOriginal: String,
        val nome: String,
        val videoUrl: String?,
    ) {
        val observacoes = mutableListOf<String>()
        val series = mutableListOf<SeriePrescrita>()
    }

    private class BlocoEmMontagem(
        val categoria: CategoriaInfo,
        val secao: String,
        val titulo: String?,
    ) {
        val linhas = mutableListOf<String>()
    }

    private class Leitor(private val doc: Document) {
        private val refeicoes = mutableListOf<Refeicao>()
        private val treinos = mutableListOf<Treino>()
        private val prs = mutableListOf<Pr>()
        private val blocos = mutableListOf<BlocoInfo>()
        private val avisos = mutableListOf<String>()

        private var modo = Modo.INFO
        private var secao = ""
        private var categoriaDaSecao = CategoriaInfo.OUTROS
        private var tituloDieta: String? = null
        private var tituloTreino: String? = null
        private var semana: Int? = null

        private var refeicao: RefeicaoEmMontagem? = null
        private var treino: TreinoEmMontagem? = null
        private var exercicio: ExercicioEmMontagem? = null
        private var bloco: BlocoEmMontagem? = null

        fun ler(): Plano {
            val conteudo = doc.selectFirst("div.content") ?: doc.body()
            for (el in conteudo.children()) {
                when (el.normalName()) {
                    "h1" -> secaoGrande(el)
                    "h2" -> subtitulo(el)
                    "h3" -> titulo3(el)
                    "details" -> opcaoOuTexto(el)
                    "ul", "ol" -> lista(el)
                    "p" -> paragrafo(el)
                    "table" -> tabela(el)
                    "hr", "br" -> Unit
                    else -> textoSolto(el)
                }
            }
            fecharTudo()

            val cabecalho = doc.selectFirst(".header")
            val textoCompleto = conteudo.text()
            val textoCardio = blocos.filter { it.categoria == CategoriaInfo.CARDIO }
                .joinToString(" ") { (it.titulo ?: "") + " " + it.linhas.joinToString(" ") }

            return Plano(
                nomeAluno = cabecalho?.selectFirst("h1")?.text()?.trim()?.ifEmpty { null },
                geradoEm = dataDeGeracao(cabecalho?.text() ?: doc.text()),
                semana = semana,
                tituloTreino = tituloTreino,
                tituloDieta = tituloDieta,
                refeicoes = refeicoes,
                treinos = treinos,
                prs = prs,
                blocos = blocos,
                metas = Metas(
                    aguaLitros = AGUA.find(textoCompleto)?.groupValues?.get(1)?.numero(),
                    cardioDiasSemana = DIAS_NA_SEMANA.find(textoCardio)?.groupValues?.get(1)?.toIntOrNull(),
                    cardioMinutos = MINUTOS_POR_DIA.find(textoCardio)?.groupValues?.get(1)?.toIntOrNull(),
                ),
                avisos = avisos,
            )
        }

        // ---------- títulos ----------

        private fun secaoGrande(el: Element) {
            fecharTudo()
            secao = el.text().trim()
            val n = semAcento(secao)
            categoriaDaSecao = when {
                "dieta" in n -> CategoriaInfo.DIETA
                "treino" in n -> CategoriaInfo.TREINO
                else -> CategoriaInfo.OUTROS
            }
            if (categoriaDaSecao == CategoriaInfo.DIETA && tituloDieta == null) tituloDieta = secao
            if (categoriaDaSecao == CategoriaInfo.TREINO && "semana" in n) {
                tituloTreino = secao
                semana = SEMANA.find(n)?.groupValues?.get(1)?.toIntOrNull()
            }
            modo = Modo.INFO
        }

        private fun subtitulo(el: Element) {
            fecharTudo()
            val texto = el.text().trim()
            val n = semAcento(texto)
            val comoTreino = TITULO_DE_TREINO.find(n)
            when {
                comoTreino != null -> {
                    modo = Modo.TREINO
                    treino = TreinoEmMontagem(
                        nome = semSimbolosNoInicio(texto),
                        letra = comoTreino.groupValues[1].uppercase(),
                    )
                }
                "refeic" in n && categoriaDaSecao == CategoriaInfo.DIETA -> modo = Modo.REFEICOES
                else -> {
                    modo = Modo.INFO
                    val categoria = if ("cardio" in n) CategoriaInfo.CARDIO else categoriaDaSecao
                    bloco = BlocoEmMontagem(categoria, secao, texto)
                }
            }
        }

        private fun titulo3(el: Element) {
            val texto = el.text().trim()
            when (modo) {
                Modo.REFEICOES -> {
                    fecharRefeicao()
                    refeicao = RefeicaoEmMontagem(texto)
                }
                Modo.TREINO -> exercicio?.observacoes?.add(texto) ?: avisar("Título fora de lugar", texto)
                Modo.INFO -> {
                    // Mantém a categoria do bloco em que o h3 apareceu (ex.: dentro de Cardio).
                    val categoria = bloco?.categoria ?: categoriaDaSecao
                    fecharBloco()
                    bloco = BlocoEmMontagem(categoria, secao, texto)
                }
            }
        }

        // ---------- conteúdo ----------

        private fun opcaoOuTexto(el: Element) {
            val nome = el.selectFirst("summary")?.text()?.trim()
            val itens = el.select("li").filter { it.parents().none { p -> p.normalName() == "li" } }
            val r = refeicao
            if (modo == Modo.REFEICOES && r != null) {
                r.opcoes += OpcaoRefeicao(nome?.ifEmpty { null }, itens.map(::itemDeRefeicao))
            } else {
                if (!nome.isNullOrEmpty()) linhaDeTexto(nome)
                itens.forEach { linhaDeTexto("• " + textoProprio(it)) }
            }
        }

        private fun lista(el: Element) {
            val itens = el.children().filter { it.normalName() == "li" }
            when (modo) {
                Modo.REFEICOES -> {
                    val r = refeicao
                    if (r != null) r.opcoes += OpcaoRefeicao(null, itens.map(::itemDeRefeicao))
                    else itens.forEach { avisar("Item fora de uma refeição", it.text()) }
                }
                Modo.TREINO -> {
                    val e = exercicio
                    if (e == null) {
                        itens.forEach { avisar("Série sem exercício", it.text()) }
                        return
                    }
                    for (li in itens) {
                        val serie = SerieParser.parse(textoProprio(li), textoAninhado(li))
                        if (!serie.interpretada) avisar("Série fora do padrão", serie.textoOriginal)
                        e.series += serie
                    }
                }
                Modo.INFO -> for (li in itens) {
                    linhaDeTexto("• " + textoProprio(li))
                    textoAninhado(li)?.let { linhaDeTexto("  " + it) }
                }
            }
        }

        private fun paragrafo(el: Element) {
            val texto = el.text().trim()
            if (texto.isEmpty()) return
            when (modo) {
                Modo.TREINO -> {
                    if (ehCabecalhoDeExercicio(el)) novoExercicio(el)
                    else exercicio?.observacoes?.add(texto) ?: avisar("Texto solto no treino", texto)
                }
                Modo.REFEICOES -> linhaDeTexto(texto)
                Modo.INFO -> if (!ROTULO_PRS.matches(texto)) linhaDeTexto(texto)
            }
        }

        private fun tabela(el: Element) {
            val linhas = el.select("tr").map { tr -> tr.select("th, td").map { it.text().trim() } }
                .filter { it.isNotEmpty() }
            val cabecalho = linhas.firstOrNull()?.map(::semAcento).orEmpty()
            val ehTabelaDePrs = categoriaDaSecao == CategoriaInfo.TREINO && prs.isEmpty() &&
                cabecalho.size >= 2 && cabecalho.any { "carga" in it }
            if (ehTabelaDePrs) {
                for (colunas in linhas.drop(1)) {
                    if (colunas.size < 2 || colunas[0].isEmpty()) continue
                    prs += Pr(
                        exercicio = colunas[0],
                        texto = colunas[1],
                        cargaKg = KG.find(colunas[1])?.groupValues?.get(1)?.numero(),
                    )
                }
            } else {
                linhas.forEach { linhaDeTexto(it.joinToString(" — ")) }
            }
        }

        private fun textoSolto(el: Element) {
            val texto = el.text().trim()
            if (texto.isEmpty()) return
            if (modo == Modo.TREINO) {
                exercicio?.observacoes?.add(texto) ?: avisar("Texto solto no treino", texto)
            } else {
                linhaDeTexto(texto)
            }
        }

        // ---------- exercício ----------

        private fun ehCabecalhoDeExercicio(p: Element): Boolean {
            val primeiro = p.children().firstOrNull() ?: return false
            if (primeiro.normalName() != "strong") return false
            // Nada de texto antes do negrito.
            val antes = p.childNodes().takeWhile { it !== primeiro }
            return antes.all { it.outerHtml().isBlank() }
        }

        private fun novoExercicio(p: Element) {
            fecharExercicio()
            val video = p.selectFirst("a[href]")?.attr("href")?.trim()?.ifEmpty { null }

            val copia = p.clone()
            copia.select("a").remove()
            val bruto = ESPACOS.replace(copia.selectFirst("strong")?.text().orEmpty(), " ").trim()
            copia.select("strong").remove()
            val fora = PARENTESES_VAZIOS.replace(copia.text(), " ").let { ESPACOS.replace(it, " ").trim() }

            // Parênteses curtos são variação do exercício ("(supinada)") e ficam no nome.
            // Os longos são recado ("(Igual vc fez semana passada)") e viram observação.
            val recados = mutableListOf<String>()
            val nome = PARENTESES.replace(bruto) { m ->
                val dentro = m.groupValues[1].trim()
                if (dentro.split(ESPACOS).size <= 2) m.value else {
                    recados += dentro
                    " "
                }
            }.let { ESPACOS.replace(it, " ").trim() }.ifEmpty { bruto }

            val e = ExercicioEmMontagem(nomeOriginal = bruto, nome = nome, videoUrl = video)
            e.observacoes += recados
            if (fora.isNotEmpty()) e.observacoes += semParentesesDeFora(fora)
            exercicio = e
        }

        // ---------- fechamento ----------

        private fun fecharTudo() {
            fecharExercicio()
            treino?.let { t ->
                treinos += Treino(t.nome, t.letra, t.exercicios.map { e ->
                    val obs = e.observacoes.filter { it.isNotBlank() }.joinToString("\n").ifEmpty { null }
                    // Sem lista de séries: procura a série escrita solta na observação.
                    val achadas = if (e.series.isEmpty()) SerieParser.seriesDaObservacao(obs) else null
                    Exercicio(
                        nomeOriginal = e.nomeOriginal,
                        nome = e.nome,
                        chave = ESPACOS.replace(semAcento(e.nome), " ").trim(),
                        observacao = if (achadas != null) achadas.observacao else obs,
                        videoUrl = e.videoUrl,
                        series = achadas?.series ?: e.series.toList(),
                    )
                })
            }
            treino = null
            fecharRefeicao()
            fecharBloco()
        }

        private fun fecharExercicio() {
            val e = exercicio ?: return
            val t = treino
            if (t != null) t.exercicios += e else avisar("Exercício fora de um treino", e.nomeOriginal)
            exercicio = null
        }

        private fun fecharRefeicao() {
            refeicao?.let { refeicoes += Refeicao(it.titulo, it.opcoes.toList()) }
            refeicao = null
        }

        private fun fecharBloco() {
            bloco?.let { b ->
                if (b.linhas.isNotEmpty()) blocos += BlocoInfo(b.categoria, b.secao, b.titulo, b.linhas.toList())
            }
            bloco = null
        }

        // ---------- utilidades ----------

        private fun linhaDeTexto(texto: String) {
            if (texto.isBlank()) return
            val b = bloco ?: BlocoEmMontagem(categoriaDaSecao, secao, null).also { bloco = it }
            b.linhas += texto.trimEnd()
        }

        private fun avisar(motivo: String, texto: String) {
            avisos += "$motivo: ${texto.take(120)}"
        }

        private fun itemDeRefeicao(li: Element): ItemRefeicao {
            val texto = textoProprio(li)
            return ItemRefeicao(texto, OPCIONAL.containsMatchIn(texto))
        }
    }

    // ---------- funções puras ----------

    private val ESPACOS = Regex("""\s+""")
    private val PARENTESES = Regex("""\(([^()]*)\)""")
    private val PARENTESES_VAZIOS = Regex("""\(\s*\)""")
    private val OPCIONAL = Regex("""\bopcional\b""", RegexOption.IGNORE_CASE)
    private val ROTULO_PRS = Regex("""^\s*PRs?\s*:?\s*$""", RegexOption.IGNORE_CASE)
    private val KG = Regex("""(\d+(?:[.,]\d+)?)\s*kg""", RegexOption.IGNORE_CASE)
    private val SEMANA = Regex("""(\d+)\s*[ªºa]?\s*semana""")
    private val TITULO_DE_TREINO = Regex("""\btreino\s+([a-z]|\d{1,2})\s*$""")
    private val DATA = Regex("""(\d{2})/(\d{2})/(\d{4})(?:\s+(\d{2}):(\d{2}))?""")
    private val AGUA = Regex("""(\d+(?:[.,]\d+)?)\s*L\s+de\s+[áa]gua""", RegexOption.IGNORE_CASE)
    private val DIAS_NA_SEMANA = Regex("""(\d+)\s*(?:dias?|x|vezes)\s+(?:na|por)\s+semana""", RegexOption.IGNORE_CASE)
    private val MINUTOS_POR_DIA = Regex("""(\d+)\s*min(?:utos)?\s+por\s+dia""", RegexOption.IGNORE_CASE)
    private val MARCAS = Regex("""\p{Mn}+""")

    /** Minúsculas e sem acento, para comparar texto digitado à mão. */
    internal fun semAcento(texto: String): String =
        MARCAS.replace(Normalizer.normalize(texto, Normalizer.Form.NFD), "").lowercase()

    /** Tira o emoji que o Notion põe antes do título ("💪 Treino A"). */
    private fun semSimbolosNoInicio(texto: String): String =
        texto.dropWhile { !it.isLetterOrDigit() }.trim().ifEmpty { texto }

    private fun semParentesesDeFora(texto: String): String =
        if (texto.startsWith("(") && texto.endsWith(")") && texto.indexOf(')') == texto.length - 1) {
            texto.substring(1, texto.length - 1).trim()
        } else {
            texto
        }

    /** Texto do item sem a lista recuada que vem dentro dele. */
    private fun textoProprio(li: Element): String {
        val copia = li.clone()
        copia.children().filter { it.normalName() == "ul" || it.normalName() == "ol" }.forEach { it.remove() }
        return ESPACOS.replace(copia.text(), " ").trim()
    }

    /** Texto da lista recuada dentro do item (a "Obs:" do treinador). */
    private fun textoAninhado(li: Element): String? =
        li.children().filter { it.normalName() == "ul" || it.normalName() == "ol" }
            .joinToString(" ") { it.text().trim() }
            .trim().ifEmpty { null }

    private fun dataDeGeracao(texto: String): String? {
        val m = DATA.find(texto) ?: return null
        val (dia, mes, ano, hora, minuto) = m.destructured
        return if (hora.isEmpty()) "$ano-$mes-$dia" else "$ano-$mes-${dia}T$hora:$minuto"
    }

    private fun String.numero(): Double? = replace(',', '.').toDoubleOrNull()
}
