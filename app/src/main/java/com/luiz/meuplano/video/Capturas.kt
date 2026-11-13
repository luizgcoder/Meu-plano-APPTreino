package com.luiz.meuplano.video

import java.io.File

/*
 * A galeria da aba Câmera: fotos e vídeos livres (aquecimento, backoff...), fora das
 * séries do plano. Cada arquivo carrega no nome o tipo e a hora, então não precisa
 * de tabela no banco: "BACKOFF_1760400000000.mp4".
 */

/** Para que foi a captura. */
enum class Marca(val rotulo: String) {
    AQUECIMENTO("Aquecimento"),
    BACKOFF("Backoff"),
    LIVRE("Livre"),
}

/**
 * @param criadaEm momento da captura, em milissegundos
 */
data class Captura(val arquivo: File, val marca: Marca, val criadaEm: Long) {
    val foto: Boolean get() = arquivo.extension.equals(EXTENSAO_DA_FOTO, ignoreCase = true)
}

const val EXTENSAO_DA_FOTO = "jpg"
const val EXTENSAO_DO_VIDEO = "mp4"

private val NOME = Regex("""^([A-Z]+)_(\d{10,15})\.(jpg|mp4)$""")

/** "BACKOFF_1760400000000.mp4" */
fun nomeDaCaptura(marca: Marca, criadaEm: Long, foto: Boolean): String =
    "${marca.name}_$criadaEm.${if (foto) EXTENSAO_DA_FOTO else EXTENSAO_DO_VIDEO}"

/** Lê o nome do arquivo. Nulo se ele não segue o padrão (arquivo estranho na pasta). */
fun lerCaptura(arquivo: File): Captura? {
    val m = NOME.find(arquivo.name) ?: return null
    val marca = Marca.entries.firstOrNull { it.name == m.groupValues[1] } ?: return null
    val momento = m.groupValues[2].toLongOrNull() ?: return null
    return Captura(arquivo, marca, momento)
}

/** As capturas da pasta, da mais nova para a mais velha. */
fun capturasDaPasta(arquivos: List<File>): List<Captura> =
    arquivos.mapNotNull(::lerCaptura).sortedByDescending { it.criadaEm }
