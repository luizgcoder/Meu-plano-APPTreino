package com.luiz.meuplano.trocas

import kotlin.math.abs

/*
 * A conta do "Montador de Trocas": trocar um alimento da dieta por um
 * ou dois outros mantendo as mesmas calorias. As regras de arredondamento são as
 * mesmas do material do treinador, para o app dar exatamente os mesmos números.
 */

enum class Categoria(val rotulo: String, val plural: String) {
    CARB("Carboidrato", "Carboidratos"),
    PTN("Proteína", "Proteínas"),
    FRUTA("Fruta", "Frutas"),
    GORD("Gordura", "Gorduras"),
}

/**
 * Um alimento do banco. Os valores são por 100 g do alimento já preparado.
 *
 * @param comoPesar em que estado pesar: "cozido", "grelhado", "unidade"...
 * @param estimado verdadeiro quando o valor não é da TACO (rótulo ou USDA)
 * @param gramasPorUnidade peso de uma unidade (um ovo, uma fatia), ou 0 se o alimento é pesado em gramas
 */
data class Alimento(
    val nome: String,
    val categoria: Categoria,
    val kcal: Double,
    val proteina: Double,
    val gordura: Double,
    val carbo: Double,
    val comoPesar: String,
    val estimado: Boolean,
    val gramasPorUnidade: Int,
) {
    /** Contado em unidades (ovo, fatia de pão, banana) em vez de pesado. */
    val emUnidades: Boolean get() = gramasPorUnidade > 0

    val kcalPorUnidade: Double get() = kcal * gramasPorUnidade / 100
}

/** Calorias e macros de uma quantidade de comida, em kcal e gramas. */
data class Macros(val kcal: Double, val proteina: Double, val carbo: Double, val gordura: Double)

/**
 * Quanto comer de um alimento.
 *
 * @param unidades quantas unidades, ou nulo se o alimento é pesado em gramas
 * @param gramas o peso (para os de unidade, o peso aproximado das unidades)
 */
data class Porcao(val alimento: Alimento, val unidades: Int?, val gramas: Double, val macros: Macros)

/**
 * O resultado de uma troca.
 *
 * @param kcalAlvo as calorias do alimento original, que a troca tenta bater
 * @param porcoes o que comer no lugar: uma ou duas porções
 * @param de os macros do alimento original
 * @param para a soma dos macros das porções novas
 * @param aviso preenchido quando a conta não fecha (ver [calcularTroca])
 */
data class Troca(
    val kcalAlvo: Double,
    val porcoes: List<Porcao>,
    val de: Macros,
    val para: Macros,
    val aviso: String?,
)

/** Um exemplo pronto de troca, como os "atalhos rápidos" do material do treinador. */
data class Atalho(
    val rotulo: String,
    val origem: String,
    val quantidade: Double,
    val destinos: List<String>,
    /** Quanto das calorias vai no primeiro alimento, de 0 a 100. Só vale com dois destinos. */
    val proporcao: Int = 50,
)

val ATALHOS: List<Atalho> = listOf(
    Atalho("Arroz → Batata-doce", "Arroz branco cozido", 150.0, listOf("Batata-doce cozida")),
    Atalho("Arroz + Feijão", "Arroz branco cozido", 200.0, listOf("Arroz branco cozido", "Feijão carioca cozido"), 60),
    Atalho("Frango → Patinho", "Frango (peito) grelhado", 150.0, listOf("Patinho grelhado")),
    Atalho(
        "Hambúrguer: pão Bauducco + batata", "Mandioca cozida", 200.0,
        listOf("Pão de hambúrguer Bauducco", "Batata inglesa cozida"), 45,
    ),
    Atalho("Banana → Mamão", "Banana", 2.0, listOf("Mamão")),
    Atalho(
        "Frango → Whey + ovo", "Frango (peito) grelhado", 150.0,
        listOf("Whey 100% Integral Médica (pó)", "Ovo de galinha cozido"), 60,
    ),
)

const val AVISO_PASSOU_DO_TOTAL =
    "O 1º alimento (em unidade inteira) já passou do total — ajuste a proporção ou a quantidade."

/** Arredonda como o JavaScript do material: meio para cima. */
private fun arredondar(x: Double): Long = Math.round(x)

/**
 * Os gramas de um alimento arredondados para um peso fácil de medir na balança:
 * de 1 em 1 g nos muito calóricos (250 kcal ou mais por 100 g), de 10 em 10 g a partir
 * de 300 g e de 5 em 5 g no resto.
 */
private fun arredondarGramas(gramas: Double, alimento: Alimento): Double {
    val passo = if (alimento.kcal >= 250) 1 else if (gramas >= 300) 10 else 5
    return (arredondar(gramas / passo) * passo).toDouble()
}

private fun macrosDe(alimento: Alimento, gramas: Double, kcal: Double) = Macros(
    kcal = kcal,
    proteina = alimento.proteina * gramas / 100,
    carbo = alimento.carbo * gramas / 100,
    gordura = alimento.gordura * gramas / 100,
)

/** O peso em gramas de uma [quantidade] do alimento: unidades viram gramas, gramas ficam como estão. */
fun gramasDe(alimento: Alimento, quantidade: Double): Double =
    if (alimento.emUnidades) quantidade * alimento.gramasPorUnidade else quantidade

/** Os macros de uma [quantidade] do alimento (em unidades, se ele é de unidade; senão em gramas). */
fun macrosDaQuantidade(alimento: Alimento, quantidade: Double): Macros {
    val gramas = gramasDe(alimento, quantidade.coerceAtLeast(0.0))
    return macrosDe(alimento, gramas, alimento.kcal * gramas / 100)
}

/**
 * Quanto comer de [alimento] para chegar perto de [kcal]. Os de unidade saem em
 * unidades inteiras (no mínimo uma); os outros, em gramas arredondados.
 */
fun porcaoPara(alimento: Alimento, kcal: Double): Porcao {
    val alvo = kcal.coerceAtLeast(0.0)
    if (alimento.emUnidades) {
        val unidades = maxOf(1L, arredondar(alvo / alimento.kcalPorUnidade)).toInt()
        val gramas = (unidades * alimento.gramasPorUnidade).toDouble()
        return Porcao(alimento, unidades, gramas, macrosDe(alimento, gramas, unidades * alimento.kcalPorUnidade))
    }
    val gramas = arredondarGramas(alvo / (alimento.kcal / 100), alimento)
    return Porcao(alimento, null, gramas, macrosDe(alimento, gramas, gramas * alimento.kcal / 100))
}

/**
 * Troca [quantidade] de [origem] por [destino1] ou, com [destino2], pelos dois juntos.
 *
 * Com dois destinos, [proporcao] (0 a 100) é quanto das calorias vai no primeiro; o segundo
 * fica com o que sobrar depois do arredondamento do primeiro. Se o primeiro, por ser de
 * unidade inteira, já passa do total, o resultado vem com [Troca.aviso].
 *
 * @param quantidade em unidades se a origem é de unidade; senão em gramas
 */
fun calcularTroca(
    origem: Alimento,
    quantidade: Double,
    destino1: Alimento,
    destino2: Alimento? = null,
    proporcao: Int = 50,
): Troca {
    val de = macrosDaQuantidade(origem, quantidade)
    var aviso: String? = null
    val porcoes = if (destino2 != null) {
        val fracao = proporcao.coerceIn(0, 100) / 100.0
        val primeira = porcaoPara(destino1, de.kcal * fracao)
        var resto = de.kcal - primeira.macros.kcal
        if (resto < 0) {
            aviso = AVISO_PASSOU_DO_TOTAL
            resto = 0.0
        }
        listOf(primeira, porcaoPara(destino2, resto))
    } else {
        listOf(porcaoPara(destino1, de.kcal))
    }
    val para = Macros(
        kcal = porcoes.sumOf { it.macros.kcal },
        proteina = porcoes.sumOf { it.macros.proteina },
        carbo = porcoes.sumOf { it.macros.carbo },
        gordura = porcoes.sumOf { it.macros.gordura },
    )
    return Troca(de.kcal, porcoes, de, para, aviso)
}

/**
 * A quantidade ao escolher outro alimento de origem: um número que era de gramas não serve
 * para unidades (150 ovos) e vice-versa, então nesses casos volta para um valor de partida.
 */
fun quantidadeAoMudarOrigem(novo: Alimento, quantidadeAtual: Double): Double = when {
    novo.emUnidades && quantidadeAtual > 20 -> 1.0
    !novo.emUnidades && quantidadeAtual < 20 -> 150.0
    else -> quantidadeAtual
}

/** Os alimentos que podem entrar no lugar de [origem]: só os da mesma categoria, ou todos. */
fun destinosPara(origem: Alimento, todasAsCategorias: Boolean): List<Alimento> =
    if (todasAsCategorias) BancoDeAlimentos.ALIMENTOS else BancoDeAlimentos.daCategoria(origem.categoria)

/**
 * O alimento de destino depois que a origem ou o filtro de categoria mudou: mantém o
 * [atual] se ele ainda pode ser escolhido; senão, o primeiro permitido que não seja a
 * própria origem nem um dos nomes em [evitar].
 */
fun destinoValido(atual: String?, origem: Alimento, todasAsCategorias: Boolean, evitar: Set<String> = emptySet()): String {
    val permitidos = destinosPara(origem, todasAsCategorias)
    if (atual != null && permitidos.any { it.nome == atual }) return atual
    val outro = permitidos.firstOrNull { it.nome != origem.nome && it.nome !in evitar } ?: permitidos.first()
    return outro.nome
}

// ---------- textos ----------

/** 192.5 -> "193" */
fun inteiro(x: Double): String = arredondar(x).toString()

/** Uma casa decimal só quando precisa: 192.52 -> "192,5", 46.0 -> "46". */
fun umaCasa(x: Double): String {
    val v = arredondar(x * 10) / 10.0
    return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',')
}

/** "250 g" ou "2 un" */
fun Porcao.quantidadeTexto(): String = if (unidades != null) "$unidades un" else "${inteiro(gramas)} g"

/** "pesar cozida · 193 kcal" ou, para os de unidade, "≈ 196 g · pesar unidade · 180 kcal" */
fun Porcao.detalhe(): String {
    val peso = if (unidades != null) "≈ ${inteiro(gramas)} g · " else ""
    return "${peso}pesar ${alimento.comoPesar} · ${inteiro(macros.kcal)} kcal"
}

/** A quantidade da origem como se escreve na dieta: "150 g" ou "2 un". */
fun quantidadeDaOrigemTexto(alimento: Alimento, quantidade: Double): String =
    if (alimento.emUnidades) "${umaCasa(quantidade)} un" else "${umaCasa(quantidade)} g"

/** Para onde foi um macro com a troca. */
enum class Sentido { IGUAL, SUBIU, DESCEU }

/** A diferença de um macro: "igual", "+3,9" ou "−2,3". Menos de meio grama (ou meia kcal) conta como igual. */
fun diferenca(de: Double, para: Double): Pair<String, Sentido> {
    val d = para - de
    return when {
        abs(d) < 0.5 -> "igual" to Sentido.IGUAL
        d > 0 -> "+${umaCasa(abs(d))}" to Sentido.SUBIU
        else -> "−${umaCasa(abs(d))}" to Sentido.DESCEU
    }
}

/** "74 kcal/un (50g)" para os de unidade; nulo para os pesados. */
fun Alimento.porUnidadeTexto(): String? =
    if (emUnidades) "${inteiro(kcalPorUnidade)} kcal/un (${gramasPorUnidade}g)" else null
