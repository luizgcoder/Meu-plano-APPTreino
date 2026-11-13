package com.luiz.meuplano.ui

import com.luiz.meuplano.data.SerieFeitaEntity
import com.luiz.meuplano.data.SeriePrescritaEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 90.0 -> "90", 7.5 -> "7,5" */
fun Double.semZeros(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString().replace('.', ',')

/**
 * A série em formato curto e sempre igual, qualquer que tenha sido a escrita do treinador:
 * "8x1 · 90kg · RPE 7", "3x4–6 · RPE 9", "2x2 · −10% da série anterior".
 * Se a linha fugiu do padrão, devolve o texto original.
 */
fun SeriePrescritaEntity.resumo(): String {
    val s = series
    val min = repsMin
    if (!interpretada || s == null || min == null) return textoOriginal

    val partes = mutableListOf<String>()

    val max = repsMax
    val reps = if (max != null && max != min) "$min–$max" else "$min"
    partes += "${s}x$reps" + (unidade?.let { " $it" } ?: "")

    val cMin = cargaMinKg
    val cMax = cargaMaxKg
    val carga = when {
        cMin == null -> null
        cMax != null && cMax != cMin -> "${cMin.semZeros()}–${cMax.semZeros()}kg"
        cargaPorLado -> "${cMin.semZeros()}kg de cada lado"
        else -> "${cMin.semZeros()}kg"
    }
    when {
        pesoCorporal && carga != null -> partes += "peso corporal + $carga"
        pesoCorporal -> partes += "peso corporal"
        carga != null -> partes += carga
    }

    ajustePercentual?.let { a ->
        val sinal = if (a < 0) "−" else "+"
        partes += "$sinal${kotlin.math.abs(a).semZeros()}% da série anterior"
    }

    val rMin = rpeMin
    val rMax = rpeMax
    if (rMin != null) {
        partes += if (rMax != null && rMax != rMin) "RPE ${rMin.semZeros()}–${rMax.semZeros()}" else "RPE ${rMin.semZeros()}"
    }

    return partes.joinToString(" · ")
}

/** "2026-10-05T19:03" -> "05/10/2026" */
fun dataCurta(iso: String?): String? {
    if (iso == null || iso.length < 10) return null
    val (ano, mes, dia) = iso.substring(0, 10).split("-").takeIf { it.size == 3 } ?: return null
    return "$dia/$mes/$ano"
}

/** O que já foi registrado na série: "90kg · RPE 7 · vídeo cortado (18s)". Sem números, só "feito". */
fun SerieFeitaEntity.resumo(): String {
    val partes = mutableListOf<String>()
    cargaKg?.let { partes += "${it.semZeros()}kg" }
    rpe?.let { partes += "RPE ${it.semZeros()}" }
    if (videoArquivo != null) {
        val inicio = corteInicioMs
        val fim = corteFimMs
        partes += if (inicio != null && fim != null) "vídeo cortado (${duracaoCurta(fim - inicio)})" else "vídeo"
    }
    return if (partes.isEmpty()) "feito" else partes.joinToString(" · ")
}

/**
 * Carga que já vem preenchida ao registrar a série:
 * a que você registrou antes, a carga fixa do plano, ou a conta do
 * "diminuir 10% da série anterior" em cima do que foi registrado na linha de cima.
 */
fun sugestaoDeCarga(serie: SeriePrescritaEntity, registro: SerieFeitaEntity?, cargaDaLinhaAnterior: Double?): Double? {
    registro?.cargaKg?.let { return it }
    val min = serie.cargaMinKg
    if (min != null && min == serie.cargaMaxKg) return min
    val ajuste = serie.ajustePercentual
    if (ajuste != null && cargaDaLinhaAnterior != null) {
        // Arredonda para 0,5 kg, que é o menor passo que dá para montar na barra.
        return kotlin.math.floor(cargaDaLinhaAnterior * (1 + ajuste / 100) * 2 + 0.5) / 2
    }
    return null
}

/** Lê "92,5" ou "92.5". Vazio ou inválido vira nulo. */
fun lerNumero(texto: String): Double? =
    texto.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }

/** 18300 -> "18s", 75000 -> "1min15s" */
fun duracaoCurta(ms: Long): String {
    val total = (ms + 500) / 1000
    return if (total < 60) "${total}s" else "${total / 60}min${total % 60}s"
}

/** Posição dentro do vídeo, com décimos: 3200 -> "0:03,2" */
fun tempoPreciso(ms: Long): String {
    val decimos = ms.coerceAtLeast(0) / 100
    return "%d:%02d,%d".format(decimos / 600, (decimos / 10) % 60, decimos % 10)
}

/** 1500 -> "1,5 L", 4000 -> "4 L" */
fun litros(ml: Int): String = "${(ml / 1000.0).semZeros()} L"

private val DIA_DA_SEMANA_E_DATA = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("pt-BR"))

/** "Hoje · sex. 09/10", "Ontem · qui. 08/10" ou só "qua. 07/10" */
fun rotuloDoDia(dia: LocalDate, hoje: LocalDate): String {
    val data = dia.format(DIA_DA_SEMANA_E_DATA)
    return when (ChronoUnit.DAYS.between(dia, hoje)) {
        0L -> "Hoje · $data"
        1L -> "Ontem · $data"
        else -> data
    }
}

/** Tamanho de arquivo: 8600000 -> "8,2 MB" */
fun megabytes(bytes: Long): String {
    val mb = Math.round(bytes / (1024.0 * 1024.0) * 10) / 10.0
    return "${mb.semZeros()} MB"
}

private val SEM_LETRA_OU_NUMERO = Regex("""[^A-Za-z0-9]+""")
private val ACENTO = Regex("""\p{Mn}+""")
private val MOMENTO_NO_NOME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

/** Nome do vídeo na galeria: "Agachamento low bar" -> "MeuPlano_Agachamento_low_bar_20261013_171205.mp4" */
fun nomeNaGaleria(base: String, momento: java.time.LocalDateTime): String {
    val semAcento = ACENTO.replace(java.text.Normalizer.normalize(base, java.text.Normalizer.Form.NFD), "")
    val limpo = SEM_LETRA_OU_NUMERO.replace(semAcento, "_").trim('_').take(40).ifEmpty { "video" }
    return "MeuPlano_${limpo}_${momento.format(MOMENTO_NO_NOME)}.mp4"
}
