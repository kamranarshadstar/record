package com.example.record.data.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.example.record.security.SecurityManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [AudioChunk::class, ServerJob::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun audioChunkDao(): AudioChunkDao
    abstract fun serverJobDao(): ServerJobDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Initialize SQLCipher
                System.loadLibrary("sqlcipher")
                
                val passphrase = SecurityManager.getDatabasePassphrase(context)
                val factory = SupportOpenHelperFactory(passphrase)

                val dbFile = context.getDatabasePath("record_database")
                if (dbFile.exists()) {
                    val config = SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name("record_database")
                        .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                            override fun onCreate(db: SupportSQLiteDatabase) {}
                            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                        })
                        .build()
                    val helper = factory.create(config)
                    try {
                        helper.readableDatabase
                    } catch (e: Exception) {
                        Log.w("AppDatabase", "Database encryption mismatch detected. Wiping for migration.")
                        helper.close()
                        context.deleteDatabase("record_database")
                    } finally {
                        try { helper.close() } catch (e: Exception) {}
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "record_database"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration(true)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
