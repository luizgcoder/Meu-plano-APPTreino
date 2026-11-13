package com.luiz.meuplano.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dá à foto o mesmo visual da câmera de vídeo, depois de tirada. */
object ProcessadorDeFoto {

    /** Linhas processadas por vez: pouca memória extra mesmo em foto de 50 MP. */
    private const val FAIXA = 128

    /**
     * Lê [origem], aplica o visual e grava em [destino] como JPEG. A orientação da foto
     * (deitada ou em pé) é copiada do arquivo original, sem girar os pixels.
     */
    suspend fun aplicarVisual(origem: File, destino: File, nivel: NivelDoVisual): Unit = withContext(Dispatchers.Default) {
        destino.parentFile?.mkdirs()
        if (nivel == NivelDoVisual.DESLIGADO) {
            origem.copyTo(destino, overwrite = true)
            return@withContext
        }
        val orientacao = runCatching { ExifInterface(origem.absolutePath).getAttribute(ExifInterface.TAG_ORIENTATION) }
            .getOrNull()
        val opcoes = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val imagem = BitmapFactory.decodeFile(origem.absolutePath, opcoes) ?: throw IOException("foto ilegível")
        try {
            val cubo = cuboDoVisual(nivel.forca)
            val largura = imagem.width
            var y = 0
            var faixa = IntArray(largura * FAIXA)
            while (y < imagem.height) {
                val linhas = min(FAIXA, imagem.height - y)
                if (faixa.size != largura * linhas) faixa = IntArray(largura * linhas)
                imagem.getPixels(faixa, 0, largura, 0, y, largura, linhas)
                aplicarCubo(faixa, cubo)
                imagem.setPixels(faixa, 0, largura, 0, y, largura, linhas)
                y += linhas
            }
            destino.outputStream().use { imagem.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } finally {
            imagem.recycle()
        }
        if (orientacao != null) {
            runCatching {
                ExifInterface(destino.absolutePath).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientacao)
                    saveAttributes()
                }
            }
        }
    }
}
