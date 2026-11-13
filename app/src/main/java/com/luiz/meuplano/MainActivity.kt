package com.luiz.meuplano

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.luiz.meuplano.ui.PlanoViewModel
import com.luiz.meuplano.ui.TelaPrincipal
import com.luiz.meuplano.ui.TemaMeuPlano

class MainActivity : ComponentActivity() {

    private val viewModel: PlanoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // O app é sempre escuro: ícones claros na barra de status e na de navegação.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Só na primeira criação: ao girar a tela o mesmo intent volta, e não é para importar de novo.
        if (savedInstanceState == null) importarSeVeioArquivo(intent)
        setContent {
            TemaMeuPlano {
                TelaPrincipal(viewModel)
            }
        }
    }

    /** O app já estava aberto e chegou outro arquivo. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importarSeVeioArquivo(intent)
    }

    private fun importarSeVeioArquivo(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            // "Abrir com" a partir do WhatsApp ou do gerenciador de arquivos
            Intent.ACTION_VIEW -> intent.data
            // Menu "Compartilhar"
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        // Só endereços "content://", que o Android confere: um "file://" mandado por outro app
        // poderia apontar para um arquivo de dentro do próprio Meu Plano.
        if (uri != null && uri.scheme == "content") viewModel.importar(uri)
    }
}
