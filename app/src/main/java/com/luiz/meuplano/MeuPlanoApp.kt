package com.luiz.meuplano

import android.app.Application
import com.luiz.meuplano.data.AppDatabase
import com.luiz.meuplano.data.PlanoRepository
import java.io.File

class MeuPlanoApp : Application() {

    /** Criados só no primeiro uso, e um só para o app inteiro. */
    val banco: AppDatabase by lazy { AppDatabase.criar(this) }

    val repositorio: PlanoRepository by lazy {
        // Pasta privada: os vídeos não aparecem na galeria. O nome "videos" também
        // está em res/xml/caminhos_arquivos.xml, que é o que permite abri-los em outro app.
        PlanoRepository(banco.planoDao(), File(filesDir, "videos"))
    }
}
