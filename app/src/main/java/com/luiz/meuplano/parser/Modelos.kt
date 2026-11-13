package com.luiz.meuplano.parser

/**
 * Resultado da leitura do HTML que o treinador envia.
 * Estas classes não dependem do Android: são só os dados do plano.
 */
data class Plano(
    val nomeAluno: String?,
    /** Data em que o plano foi gerado, no formato ISO (2026-10-05T19:03), para ordenar. */
    val geradoEm: String?,
    val semana: Int?,
    val tituloTreino: String?,
    val tituloDieta: String?,
    val refeicoes: List<Refeicao>,
    val treinos: List<Treino>,
    val prs: List<Pr>,
    val blocos: List<BlocoInfo>,
    val metas: Metas,
    /** Trechos que o parser não soube encaixar. Vazio quando tudo foi lido. */
    val avisos: List<String>,
)

/** Números que o plano cobra e que o check-in diário vai usar. */
data class Metas(
    val aguaLitros: Double?,
    val cardioDiasSemana: Int?,
    val cardioMinutos: Int?,
)

data class Refeicao(val titulo: String, val opcoes: List<OpcaoRefeicao>)

/** [nome] é nulo quando a refeição tem uma lista só, sem "Opção A/B". */
data class OpcaoRefeicao(val nome: String?, val itens: List<ItemRefeicao>)

data class ItemRefeicao(val texto: String, val opcional: Boolean)

data class Treino(val nome: String, val letra: String?, val exercicios: List<Exercicio>)

data class Exercicio(
    /** Como o treinador escreveu, com os recados entre parênteses. */
    val nomeOriginal: String,
    /** Nome sem os recados. Variações curtas, como "(supinada)", ficam. */
    val nome: String,
    /** Nome em minúsculas e sem acento, para comparar o mesmo exercício entre semanas. */
    val chave: String,
    val observacao: String?,
    val videoUrl: String?,
    val series: List<SeriePrescrita>,
)

data class SeriePrescrita(
    /** A linha exatamente como veio. É o que aparece se [interpretada] for falso. */
    val textoOriginal: String,
    val series: Int?,
    val repsMin: Int?,
    val repsMax: Int?,
    /** Unidade quando não são repetições: "passos", "seg" etc. */
    val unidade: String?,
    val cargaMinKg: Double?,
    val cargaMaxKg: Double?,
    /** Formato "10kg-10kg": a carga é a de cada lado. */
    val cargaPorLado: Boolean,
    val pesoCorporal: Boolean,
    /** Ajuste sobre a série anterior, em %: -10.0 para "diminuir 10%". */
    val ajustePercentual: Double?,
    val rpeMin: Double?,
    val rpeMax: Double?,
    val gravar: Boolean,
    val informarCarga: Boolean,
    val observacao: String?,
    /** Verdadeiro quando a linha começa no formato "séries x repetições". */
    val interpretada: Boolean,
)

data class Pr(val exercicio: String, val texto: String, val cargaKg: Double?)

enum class CategoriaInfo { DIETA, TREINO, CARDIO, OUTROS }

/** Bloco de texto para consulta: diretrizes, cardio, recomendações. */
data class BlocoInfo(
    val categoria: CategoriaInfo,
    /** Título da seção grande (o h1) em que o bloco estava. */
    val secao: String,
    val titulo: String?,
    val linhas: List<String>,
)
