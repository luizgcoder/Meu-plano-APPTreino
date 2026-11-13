package com.luiz.meuplano.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.luiz.meuplano.R

/*
 * O visual do app, tirado do design do app: fundo quase preto,
 * cartões em cinza-chumbo, laranja de destaque, Barlow no texto e Barlow Condensed
 * nos títulos e números. O app é sempre escuro.
 */

object Cores {
    val Fundo = Color(0xFF0E0E10)
    val Superficie = Color(0xFF18181B)
    /** Linhas da tabela de séries e botões selecionados. */
    val SuperficieAlta = Color(0xFF232327)
    /** Bordas dos botões e o fundo das barras de progresso. */
    val Trilho = Color(0xFF2A2A2F)
    val BordaForte = Color(0xFF3F3F46)
    val Divisor = Color(0xFF26262B)
    val BordaApagada = Color(0xFF222226)

    val Texto = Color(0xFFF4F4F5)
    val TextoSuave = Color(0xFFA1A1AA)
    val TextoApagado = Color(0xFF71717A)
    val Desligado = Color(0xFF52525B)

    val Destaque = Color(0xFFFF6B2C)
    val FeitoFundo = Color(0xFF1F2E1F)
    val FeitoTexto = Color(0xFF8BE38B)
    val Agua = Color(0xFF4DA3FF)
    val Gravar = Color(0xFFFF5C5C)
    val NovoFundo = Color(0xFF3A2215)
    val NovoTexto = Color(0xFFFFB48A)
}

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
)

/** Só para o texto do feedback, que é para ler como mensagem. */
val Mono = FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal))

/** Os estilos de texto do design, pelo papel que cumprem na tela. */
object Tipos {
    /** Linha pequena em maiúsculas acima do título da tela: "MEU PLANO · 05/10 – 09/10". */
    val Sobretitulo = TextStyle(
        fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.14.em,
    )
    val TituloDeTela = TextStyle(
        fontFamily = BarlowCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 40.sp,
    )
    val TituloDeCartao = TextStyle(
        fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 26.sp,
    )
    val TituloDeSecao = TextStyle(
        fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 24.sp,
    )
    /** Etiqueta em maiúsculas dentro dos cartões: "SÉRIE", "ÁGUA", "01 · AGORA". */
    val Rotulo = TextStyle(
        fontFamily = Barlow, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.12.em,
    )
    /** Números da tabela de séries. */
    val Numero = TextStyle(
        fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 26.sp,
    )
    val NumeroMedio = TextStyle(
        fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 20.sp,
    )
    val NumeroGrande = TextStyle(
        fontFamily = BarlowCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 38.sp,
    )
    val Corpo = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val CorpoForte = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp)
    val CorpoPequeno = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val Botao = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
}

/** As cores do design no formato que os componentes prontos (diálogo, campo de texto, chave) entendem. */
private val EsquemaEscuro = darkColorScheme(
    primary = Cores.Destaque,
    onPrimary = Cores.Fundo,
    primaryContainer = Cores.NovoFundo,
    onPrimaryContainer = Cores.NovoTexto,
    secondary = Cores.TextoSuave,
    onSecondary = Cores.Fundo,
    secondaryContainer = Cores.Trilho,
    onSecondaryContainer = Cores.Texto,
    tertiary = Cores.Agua,
    onTertiary = Cores.Fundo,
    background = Cores.Fundo,
    onBackground = Cores.Texto,
    surface = Cores.Superficie,
    onSurface = Cores.Texto,
    surfaceVariant = Cores.SuperficieAlta,
    onSurfaceVariant = Cores.TextoSuave,
    surfaceContainerLowest = Cores.Fundo,
    surfaceContainerLow = Cores.Superficie,
    surfaceContainer = Cores.Superficie,
    surfaceContainerHigh = Cores.SuperficieAlta,
    surfaceContainerHighest = Cores.SuperficieAlta,
    outline = Cores.BordaForte,
    outlineVariant = Cores.Trilho,
    error = Cores.Gravar,
    onError = Cores.Fundo,
)

/** A tipografia padrão trocada para Barlow, para os componentes prontos acompanharem o resto. */
private val Tipografia: Typography = run {
    val padrao = Typography()
    fun TextStyle.titulo() = copy(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold)
    fun TextStyle.texto() = copy(fontFamily = Barlow)
    Typography(
        displayLarge = padrao.displayLarge.titulo(),
        displayMedium = padrao.displayMedium.titulo(),
        displaySmall = padrao.displaySmall.titulo(),
        headlineLarge = padrao.headlineLarge.titulo(),
        headlineMedium = padrao.headlineMedium.titulo(),
        headlineSmall = padrao.headlineSmall.titulo(),
        titleLarge = padrao.titleLarge.titulo(),
        titleMedium = padrao.titleMedium.texto().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = padrao.titleSmall.texto().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = padrao.bodyLarge.texto(),
        bodyMedium = padrao.bodyMedium.texto(),
        bodySmall = padrao.bodySmall.texto(),
        labelLarge = padrao.labelLarge.texto().copy(fontWeight = FontWeight.Bold),
        labelMedium = padrao.labelMedium.texto().copy(fontWeight = FontWeight.SemiBold),
        labelSmall = padrao.labelSmall.texto().copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun TemaMeuPlano(conteudo: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EsquemaEscuro, typography = Tipografia, content = conteudo)
}
