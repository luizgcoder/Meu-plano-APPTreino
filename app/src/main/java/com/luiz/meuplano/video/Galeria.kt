package com.luiz.meuplano.video

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Onde os vídeos baixados aparecem: na galeria, em Filmes/MeuPlano. */
object Galeria {

    const val PASTA = "MeuPlano"

    /**
     * Copia um vídeo para a galeria do celular.
     *
     * @param nome nome do arquivo na galeria, com o ".mp4"
     * @return falso no Android 9 ou anterior, que não deixa salvar sem pedir a permissão
     *   de armazenamento (lá, o caminho é o Enviar)
     * @throws IOException se a galeria não aceitar o arquivo
     */
    suspend fun salvarVideo(contexto: Context, arquivo: File, nome: String): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            salvarNoAndroid10OuNovo(contexto, arquivo, nome, foto = false)
            true
        } else {
            false
        }
    }

    /** Como [salvarVideo], para fotos: vão para Imagens/MeuPlano. */
    suspend fun salvarFoto(contexto: Context, arquivo: File, nome: String): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            salvarNoAndroid10OuNovo(contexto, arquivo, nome, foto = true)
            true
        } else {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun salvarNoAndroid10OuNovo(contexto: Context, arquivo: File, nome: String, foto: Boolean) {
        val resolver = contexto.contentResolver
        val valores = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nome)
            put(MediaStore.MediaColumns.MIME_TYPE, if (foto) "image/jpeg" else "video/mp4")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                (if (foto) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES) + "/" + PASTA,
            )
            // Enquanto copia, o arquivo fica escondido das outras apps.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val colecao = if (foto) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val destino = resolver.insert(colecao, valores) ?: throw IOException("a galeria não aceitou o arquivo")
        try {
            val saida = resolver.openOutputStream(destino) ?: throw IOException("não deu para gravar na galeria")
            saida.use { arquivo.inputStream().use { entrada -> entrada.copyTo(it) } }
            val pronto = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(destino, pronto, null, null)
        } catch (e: Exception) {
            // Um vídeo pela metade na galeria só atrapalha.
            runCatching { resolver.delete(destino, null, null) }
            throw e
        }
    }
}
