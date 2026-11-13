package com.luiz.meuplano.trocas

import java.text.Normalizer

/*
 * Liga um item escrito na dieta ("280g arroz branco ou macarrão de arroz") a um alimento
 * do banco, para o Montador de Trocas já abrir preenchido.
 *
 * A regra é conservadora de propósito: na dúvida, não reconhece. Um alimento errado
 * daria uma troca errada, e sem reconhecer o app só deixa de oferecer o botão.
 */

/**
 * @param quantidade em unidades se o alimento é de unidade, senão em gramas. Nulo quando a
 *   dieta usa uma medida que não dá para converter ("2 colheres de pasta de amendoim").
 */
data class ItemReconhecido(val alimento: Alimento, val quantidade: Double?)

private val ACENTOS = Regex("""\p{Mn}+""")
private val NAO_LETRA = Regex("""[^a-z0-9]+""")
private val ENTRE_PARENTESES = Regex("""\(([^()]*)\)""")
private val ALTERNATIVA = Regex("""\s+ou\s+""", RegexOption.IGNORE_CASE)

/** Número no começo do item, com faixa ("150 a 200") e medida opcionais. */
private val QUANTIDADE = Regex(
    """^(\d+(?:[.,]\d+)?)(?:\s*(?:a|-|–)\s*\d+(?:[.,]\d+)?)?\s*""" +
        """(kg|gramas?|gr|g|ml|l|fatias?|unidades?|un|scoops?|colheres?|x[íi]caras?|copos?)?(?![\p{L}\d])""",
    RegexOption.IGNORE_CASE,
)

/** Palavras que não ajudam a identificar o alimento. */
private val VAZIAS = setOf("de", "da", "do", "das", "dos", "em", "na", "no", "e", "a", "o", "s", "com", "sem")

/** O modo de preparo não identifica o alimento: "arroz branco" e "arroz branco cozido" são o mesmo. */
private val PREPAROS = setOf("cozido", "cozida", "grelhado", "grelhada", "assado", "assada", "pronto", "pronta", "pele")

/**
 * Palavras de corte ou de forma, que vêm antes do nome de verdade: em "filé de frango",
 * o alimento é o frango.
 */
private val CORTES = setOf(
    "file", "peito", "coxa", "sobrecoxa", "polpa", "lata", "goma", "bife", "posta", "isca", "cubo", "tira", "pedaco",
)

private fun semAcento(texto: String): String =
    ACENTOS.replace(Normalizer.normalize(texto, Normalizer.Form.NFD), "").lowercase()

/** Plural simples vira singular ("ovos" -> "ovo"), para a dieta e o banco se encontrarem. */
private fun singular(palavra: String): String = when {
    palavra == "paes" -> "pao"
    palavra.length > 3 && palavra.endsWith("s") -> palavra.dropLast(1)
    else -> palavra
}

private fun palavras(texto: String): List<String> =
    NAO_LETRA.split(semAcento(texto)).filter { it.isNotEmpty() && it !in VAZIAS }.map(::singular)

/** Outros jeitos de a dieta chamar um alimento do banco. Cada lista é um conjunto de palavras que basta. */
private val APELIDOS: Map<String, List<List<String>>> = mapOf(
    "Arroz branco cozido" to listOf(listOf("arroz")),
    "Feijão carioca cozido" to listOf(listOf("feijao")),
    "Batata baroa (mandioquinha) cozida" to listOf(listOf("mandioquinha")),
    "Abóbora cabotiá cozida" to listOf(listOf("abobora")),
    "Milho verde (escorrido)" to listOf(listOf("milho")),
    "Cuscuz de milho cozido" to listOf(listOf("cuscuz")),
    "Aveia em flocos" to listOf(listOf("aveia")),
    "Maizena (amido de milho)" to listOf(listOf("amido", "milho")),
    "Maltodextrina (Body Action)" to listOf(listOf("malto")),
    "Pão de hambúrguer Bauducco" to listOf(listOf("pao", "hamburguer")),
    "Filé mignon bovino grelhado" to listOf(listOf("file", "mignon")),
    "Coxão mole cozido" to listOf(listOf("coxao")),
    "Músculo bovino cozido" to listOf(listOf("musculo")),
    "Fígado bovino grelhado" to listOf(listOf("figado")),
    "Lombo suíno assado" to listOf(listOf("lombo")),
    "Pernil suíno assado" to listOf(listOf("pernil")),
    "Ovo de galinha cozido" to listOf(listOf("ovo")),
    "Clara de ovo cozida" to listOf(listOf("clara")),
    "Whey 100% Integral Médica (pó)" to listOf(listOf("whey")),
    "Queijo minas frescal" to listOf(listOf("queijo", "mina")),
    "Iogurte natural desnatado" to listOf(listOf("iogurte", "desnatado")),
    "Azeite de oliva" to listOf(listOf("azeite")),
    "Pasta de amendoim integral" to listOf(listOf("pasta", "amendoim")),
    "Amendoim torrado" to listOf(listOf("amendoim")),
)

/**
 * As palavras que identificam um alimento do banco.
 *
 * @param conjuntos cada conjunto, sozinho, basta para reconhecer o alimento
 * @param extras palavras que desempatam quando aparecem ("coxa" entre os cortes de frango)
 */
private class Chave(val alimento: Alimento, val conjuntos: List<Set<String>>, val extras: Set<String>)

private val CHAVES: List<Chave> = BancoDeAlimentos.ALIMENTOS.map { alimento ->
    val extras = ENTRE_PARENTESES.findAll(alimento.nome).flatMap { palavras(it.groupValues[1]) }.toSet()
    // "Tangerina / mexerica": qualquer um dos dois nomes vale.
    val doNome = ENTRE_PARENTESES.replace(alimento.nome, " ").split("/")
        .map { parte -> palavras(parte).filter { it !in PREPAROS }.toSet() }
        .filter { it.isNotEmpty() }
    val apelidos = APELIDOS[alimento.nome].orEmpty().map { it.toSet() }
    Chave(alimento, doNome + apelidos, extras - PREPAROS)
}

/**
 * Acha no banco o alimento de um item da dieta e a quantidade pedida.
 *
 * Quando o item dá alternativas ("arroz branco ou macarrão de arroz"), vale a primeira.
 * O alimento só é reconhecido se o item começa por ele: "suco de laranja" não é laranja.
 * Devolve nulo se o alimento não está no banco.
 */
fun reconhecerItem(texto: String): ItemReconhecido? {
    val primeira = ALTERNATIVA.split(texto.trim()).firstOrNull()?.trim().orEmpty()
    if (primeira.isEmpty()) return null

    val medida = QUANTIDADE.find(primeira)
    val nome = if (medida != null) primeira.substring(medida.range.last + 1) else primeira
    val doItem = palavras(nome)
    if (doItem.isEmpty()) return null
    val presentes = doItem.toSet()
    // A palavra principal: a primeira que não é corte nem forma.
    val principal = doItem.firstOrNull { it !in CORTES } ?: doItem.first()

    var melhor: Alimento? = null
    var melhorNota = 0
    for (chave in CHAVES) {
        val conjunto = chave.conjuntos
            .filter { principal in it && presentes.containsAll(it) }
            .maxByOrNull { it.size }
            ?: continue
        // Quanto mais palavras do alimento aparecem, mais específico é o acerto.
        val nota = conjunto.size * 10 + chave.extras.count { it in presentes }
        if (nota > melhorNota) {
            melhorNota = nota
            melhor = chave.alimento
        }
    }
    val alimento = melhor ?: return null

    val numero = medida?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
    val unidade = medida?.groupValues?.get(2)?.let(::semAcento).orEmpty()
    val gramas = when {
        numero == null -> null
        unidade == "kg" -> numero * 1000
        unidade == "g" || unidade == "gr" || unidade.startsWith("grama") -> numero
        else -> null
    }
    val contagem = numero?.takeIf { unidade.isEmpty() || unidade == "un" || unidade.startsWith("unidade") || unidade.startsWith("fatia") }
    val quantidade = if (alimento.emUnidades) {
        contagem ?: gramas?.let { Math.round(it / alimento.gramasPorUnidade * 10) / 10.0 }
    } else {
        gramas
    }
    return ItemReconhecido(alimento, quantidade?.takeIf { it > 0 })
}

/** Verdadeiro se o nome do alimento contém o texto buscado, sem ligar para acentos ou maiúsculas. */
fun Alimento.combinaCom(busca: String): Boolean = semAcento(busca.trim()) in semAcento(nome)
