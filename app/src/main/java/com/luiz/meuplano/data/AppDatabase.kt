package com.luiz.meuplano.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlanoEntity::class,
        PlanoHtmlEntity::class,
        RefeicaoEntity::class,
        OpcaoRefeicaoEntity::class,
        ItemRefeicaoEntity::class,
        TreinoEntity::class,
        ExercicioEntity::class,
        SeriePrescritaEntity::class,
        SerieFeitaEntity::class,
        RefeicaoFeitaEntity::class,
        CheckinDiarioEntity::class,
        TrocaDoDiaEntity::class,
        PrEntity::class,
        BlocoInfoEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun planoDao(): PlanoDao

    companion object {
        fun criar(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "meuplano.db")
                .addMigrations(MIGRACAO_2_3, MIGRACAO_3_4, MIGRACAO_4_5, MIGRACAO_5_6, MIGRACAO_6_7)
                // Só para versões sem migração escrita (a 1, do primeiro teste): apaga e recomeça.
                .fallbackToDestructiveMigration(true)
                .build()

        /**
         * Versão 7: velocidade da câmera lenta ajustável (¼×/½×/¾×) e espelhar o vídeo.
         * Os valores padrão têm que bater com os @ColumnInfo(defaultValue) de SerieFeitaEntity.
         */
        val MIGRACAO_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `serie_feita` ADD COLUMN `velocidadeLenta` REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE `serie_feita` ADD COLUMN `espelhado` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Versão 6: edição de cada clipe (câmera lenta, congelar o final e zoom).
         * Os valores padrão têm que bater com os @ColumnInfo(defaultValue) de SerieFeitaEntity.
         */
        val MIGRACAO_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `serie_feita` ADD COLUMN `lentoInicioMs` INTEGER")
                db.execSQL("ALTER TABLE `serie_feita` ADD COLUMN `lentoFimMs` INTEGER")
                db.execSQL("ALTER TABLE `serie_feita` ADD COLUMN `congelarFinal` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `serie_feita` ADD COLUMN `zoom` REAL NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `serie_feita` ADD COLUMN `centroX` REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE `serie_feita` ADD COLUMN `centroY` REAL NOT NULL DEFAULT 0.5")
            }
        }

        /**
         * Versão 5: troca de alimento por dia. Só cria a tabela nova.
         * As colunas têm que bater exatamente com TrocaDoDiaEntity.
         */
        val MIGRACAO_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `troca_do_dia` (" +
                        "`data` TEXT NOT NULL, " +
                        "`itemId` INTEGER NOT NULL, " +
                        "`origem` TEXT NOT NULL, " +
                        "`quantidade` REAL NOT NULL, " +
                        "`destino1` TEXT NOT NULL, " +
                        "`destino2` TEXT, " +
                        "`proporcao` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`data`, `itemId`), " +
                        "FOREIGN KEY(`itemId`) REFERENCES `item_refeicao`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_troca_do_dia_itemId` " +
                        "ON `troca_do_dia` (`itemId`)",
                )
            }
        }

        /** Versão 4: anotação por série, que alimenta os destaques do feedback. */
        val MIGRACAO_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `serie_feita` ADD COLUMN `anotacao` TEXT")
            }
        }

        /**
         * Versão 3: dieta do dia e check-in. Só cria tabelas novas, sem tocar no que já existe.
         * As colunas têm que bater exatamente com RefeicaoFeitaEntity e CheckinDiarioEntity.
         */
        val MIGRACAO_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `refeicao_feita` (" +
                        "`data` TEXT NOT NULL, " +
                        "`refeicaoId` INTEGER NOT NULL, " +
                        "`opcaoId` INTEGER, " +
                        "PRIMARY KEY(`data`, `refeicaoId`), " +
                        "FOREIGN KEY(`refeicaoId`) REFERENCES `refeicao`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_refeicao_feita_refeicaoId` " +
                        "ON `refeicao_feita` (`refeicaoId`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checkin_diario` (" +
                        "`data` TEXT NOT NULL, " +
                        "`aguaMl` INTEGER NOT NULL, " +
                        "`intestino` INTEGER, " +
                        "`cardioFeito` INTEGER NOT NULL, " +
                        "`refeicaoLivre` INTEGER NOT NULL, " +
                        "`fome` INTEGER, " +
                        "`pesoKg` REAL, " +
                        "`anotacao` TEXT, " +
                        "PRIMARY KEY(`data`))",
                )
            }
        }
    }
}
