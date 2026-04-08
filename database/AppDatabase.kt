package com.hemax.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hemax.database.dao.*
import com.hemax.database.entities.*
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [UserEntity::class, ChatEntity::class, MessageEntity::class, SessionEntity::class,
        GroupEntity::class, GroupMemberEntity::class, ScheduledMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun sessionDao(): SessionDao
    abstract fun groupDao(): GroupDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao

    companion object {
        private const val DB_NAME = "hemax_encrypted.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, password: String): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                SQLiteDatabase.loadLibs(context)
                val factory = SupportFactory(password.toByteArray())
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).openHelperFactory(factory).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
