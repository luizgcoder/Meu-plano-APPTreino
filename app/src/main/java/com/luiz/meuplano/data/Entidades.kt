package com.luiz.meuplano.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Tabelas do plano importado. Cada HTML vira um plano novo, e apagar um plano
 * apaga tudo que está abaixo dele (CASCADE).
 *
 *   plano ─┬─ refeicao ── opcao_refeicao ── item_refeicao
 *          ├─ treino ── exercicio ── serie_prescrita ── serie_feita
 *          │      └────── refeicao_feita (por dia)
 *          │   item_refeicao ── troca_do_dia (por dia)
 *          ├─ pr
 *          ├─ bloco_info
 *          └─ plano_html   (o arquivo original, guardado à parte)
 *
 *   checkin_diario   (solto: um por dia, não pertence a um plano)
 */

@Entity(tableName = "plano", indices = [Index(value = ["hash"], unique = true)])
data class PlanoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nomeAluno: String?,
    /** ISO: 2026-10-05T19:03 */
    val geradoEm: String?,
    val semana: Int?,
    val tituloTreino: String?,
    val tituloDieta: String?,
    val metaAguaLitros: Double?,
    val cardioDiasSemana: Int?,
    val cardioMinutos: Int?,
    /** Momento da importação, em milissegundos. */
    val importadoEm: Long,
    /** SHA-256 do arquivo, para não importar o mesmo HTML duas vezes. */
    val hash: String,
)

@Entity(
    tableName = "plano_html",
    foreignKeys = [
        ForeignKey(
            entity = PlanoEntity::class,
            parentColumns = ["id"],
            childColumns = ["planoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlanoHtmlEntity(
    @PrimaryKey val planoId: Long,
    val html: String,
)

@Entity(
    tableName = "refeicao",
    foreignKeys = [
        ForeignKey(
            entity = PlanoEntity::class,
            parentColumns = ["id"],
            childColumns = ["planoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planoId")],
)
data class RefeicaoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planoId: Long,
    val ordem: Int,
    val titulo: String,
)

@Entity(
    tableName = "opcao_refeicao",
    foreignKeys = [
        ForeignKey(
            entity = RefeicaoEntity::class,
            parentColumns = ["id"],
            childColumns = ["refeicaoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("refeicaoId")],
)
data class OpcaoRefeicaoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val refeicaoId: Long,
    val ordem: Int,
    /** Nulo quando a refeição não tem "Opção A/B". */
    val nome: String?,
)

@Entity(
    tableName = "item_refeicao",
    foreignKeys = [
        ForeignKey(
            entity = OpcaoRefeicaoEntity::class,
            parentColumns = ["id"],
            childColumns = ["opcaoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("opcaoId")],
)
data class ItemRefeicaoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val opcaoId: Long,
    val ordem: Int,
    val texto: String,
    val opcional: Boolean,
)

@Entity(
    tableName = "treino",
    foreignKeys = [
        ForeignKey(
            entity = PlanoEntity::class,
            parentColumns = ["id"],
            childColumns = ["planoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planoId")],
)
data class TreinoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planoId: Long,
    val ordem: Int,
    val nome: String,
    val letra: String?,
)

@Entity(
    tableName = "exercicio",
    foreignKeys = [
        ForeignKey(
            entity = TreinoEntity::class,
            parentColumns = ["id"],
            childColumns = ["treinoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("treinoId"), Index("chave")],
)
data class ExercicioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treinoId: Long,
    val ordem: Int,
    val nomeOriginal: String,
    val nome: String,
    /** Nome normalizado, para achar o mesmo exercício em outras semanas. */
    val chave: String,
    val observacao: String?,
    val videoUrl: String?,
)

@Entity(
    tableName = "serie_prescrita",
    foreignKeys = [
        ForeignKey(
            entity = ExercicioEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercicioId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exercicioId")],
)
data class SeriePrescritaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exercicioId: Long,
    val ordem: Int,
    val textoOriginal: String,
    val series: Int?,
    val repsMin: Int?,
    val repsMax: Int?,
    val unidade: String?,
    val cargaMinKg: Double?,
    val cargaMaxKg: Double?,
    val cargaPorLado: Boolean,
    val pesoCorporal: Boolean,
    val ajustePercentual: Double?,
    val rpeMin: Double?,
    val rpeMax: Double?,
    val gravar: Boolean,
    val informarCarga: Boolean,
    val observacao: String?,
    val interpretada: Boolean,
)

@Entity(
    tableName = "pr",
    foreignKeys = [
        ForeignKey(
            entity = PlanoEntity::class,
            parentColumns = ["id"],
            childColumns = ["planoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planoId")],
)
data class PrEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planoId: Long,
    val ordem: Int,
    val exercicio: String,
    val texto: String,
    val cargaKg: Double?,
)

@Entity(
    tableName = "bloco_info",
    foreignKeys = [
        ForeignKey(
            entity = PlanoEntity::class,
            parentColumns = ["id"],
            childColumns = ["planoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planoId")],
)
data class BlocoInfoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planoId: Long,
    val ordem: Int,
    /** DIETA, TREINO, CARDIO ou OUTROS (ver CategoriaInfo). */
    val categoria: String,
    val secao: String,
    val titulo: String?,
    /** As linhas do bloco, separadas por quebra de linha. */
    val texto: String,
)

/**
 * O que você fez em uma linha de série do plano: carga, RPE e o vídeo.
 * É um registro por linha (a chave é a própria série prescrita), então salvar de novo substitui.
 */
@Entity(
    tableName = "serie_feita",
    foreignKeys = [
        ForeignKey(
            entity = SeriePrescritaEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriePrescritaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SerieFeitaEntity(
    @PrimaryKey val seriePrescritaId: Long,
    val cargaKg: Double?,
    val rpe: Double?,
    /** Nome do arquivo dentro da pasta de vídeos do app. */
    val videoArquivo: String?,
    /** Corte do vídeo (etapa 4). Nulos = vídeo inteiro. */
    val corteInicioMs: Long?,
    val corteFimMs: Long?,
    /** Momento do último registro, em milissegundos. */
    val atualizadoEm: Long,
    /** O que você anotou sobre a série ("apanhei na execução"). Vai para os destaques do feedback. */
    val anotacao: String?,
    /** Trecho em câmera lenta, dentro do corte. Nulos = sem lento. */
    val lentoInicioMs: Long? = null,
    val lentoFimMs: Long? = null,
    /** Segura o último quadro por um segundo no vídeo montado. */
    @ColumnInfo(defaultValue = "0") val congelarFinal: Boolean = false,
    /** Zoom do enquadramento (1 = sem zoom) e o centro dele, de 0 a 1 na imagem. */
    @ColumnInfo(defaultValue = "1") val zoom: Float = 1f,
    @ColumnInfo(defaultValue = "0.5") val centroX: Float = 0.5f,
    @ColumnInfo(defaultValue = "0.5") val centroY: Float = 0.5f,
    /** Velocidade do trecho em câmera lenta (¼×, ½× ou ¾×). */
    @ColumnInfo(defaultValue = "0.5") val velocidadeLenta: Float = 0.5f,
    /** Espelha o vídeo na horizontal. */
    @ColumnInfo(defaultValue = "0") val espelhado: Boolean = false,
)

/**
 * Refeição marcada como feita em um dia, e qual opção foi escolhida.
 * A chave é o par (dia, refeição): marcar de novo no mesmo dia substitui.
 */
@Entity(
    tableName = "refeicao_feita",
    primaryKeys = ["data", "refeicaoId"],
    foreignKeys = [
        ForeignKey(
            entity = RefeicaoEntity::class,
            parentColumns = ["id"],
            childColumns = ["refeicaoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("refeicaoId")],
)
data class RefeicaoFeitaEntity(
    /** Dia no formato ISO: 2026-10-09 */
    val data: String,
    val refeicaoId: Long,
    val opcaoId: Long?,
)

/**
 * Um alimento da dieta trocado por outro em um dia, pelo Montador de Trocas.
 * A chave é o par (dia, item da refeição): trocar de novo no mesmo dia substitui.
 *
 * Guarda só a escolha (os nomes como estão no banco de alimentos); as quantidades
 * são recalculadas na hora de mostrar.
 */
@Entity(
    tableName = "troca_do_dia",
    primaryKeys = ["data", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = ItemRefeicaoEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class TrocaDoDiaEntity(
    /** Dia no formato ISO: 2026-10-09 */
    val data: String,
    val itemId: Long,
    /** O alimento da dieta que saiu. */
    val origem: String,
    /** Quanto dele: em unidades se é alimento de unidade, senão em gramas. */
    val quantidade: Double,
    val destino1: String,
    /** O segundo alimento, quando a troca combina dois. */
    val destino2: String?,
    /** Quanto das calorias vai no primeiro alimento, de 0 a 100. */
    val proporcao: Int,
)

/**
 * Fechamento de um dia. Hoje o app só pede o "furei a dieta" (e conta a água); os outros
 * campos ficaram das versões anteriores e ainda entram no feedback quando preenchidos.
 * Não depende do plano: vale pela data,
 * então continua existindo quando chega o plano da semana seguinte.
 */
@Entity(tableName = "checkin_diario")
data class CheckinDiarioEntity(
    /** Dia no formato ISO: 2026-10-09 */
    @PrimaryKey val data: String,
    val aguaMl: Int,
    /** Foi ao banheiro no dia? Nulo = ainda não respondeu. */
    val intestino: Boolean?,
    val cardioFeito: Boolean,
    /**
     * Furou a dieta no dia. A coluna se chama "refeicaoLivre" porque era a "refeição livre"
     * das primeiras versões; o nome no banco ficou para não precisar de migração.
     */
    @ColumnInfo(name = "refeicaoLivre") val furouDieta: Boolean,
    /** Nível de fome de 0 (sem fome) a 5. Nulo = ainda não respondeu. */
    val fome: Int?,
    val pesoKg: Double?,
    val anotacao: String?,
) {
    companion object {
        fun vazio(data: String) = CheckinDiarioEntity(
            data = data,
            aguaMl = 0,
            intestino = null,
            cardioFeito = false,
            furouDieta = false,
            fome = null,
            pesoKg = null,
            anotacao = null,
        )
    }
}
