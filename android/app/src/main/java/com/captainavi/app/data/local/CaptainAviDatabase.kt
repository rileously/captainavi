package com.captainavi.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.captainavi.app.data.local.dao.AlertEventDao
import com.captainavi.app.data.local.dao.BreadcrumbDao
import com.captainavi.app.data.local.dao.CatchLogDao
import com.captainavi.app.data.local.dao.OutgoingEventDao
import com.captainavi.app.data.local.dao.TripDao
import com.captainavi.app.data.local.dao.WaypointDao
import com.captainavi.app.data.local.entity.AlertEventEntity
import com.captainavi.app.data.local.entity.BreadcrumbEntity
import com.captainavi.app.data.local.entity.CatchLogEntity
import com.captainavi.app.data.local.entity.OutgoingEventEntity
import com.captainavi.app.data.local.entity.TripEntity
import com.captainavi.app.data.local.entity.WaypointEntity
import com.captainavi.app.data.local.entity.WaypointType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TripEntity::class,
        BreadcrumbEntity::class,
        WaypointEntity::class,
        AlertEventEntity::class,
        OutgoingEventEntity::class,
        CatchLogEntity::class,
    ],
    version = 6,
    exportSchema = false
)
abstract class CaptainAviDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun breadcrumbDao(): BreadcrumbDao
    abstract fun waypointDao(): WaypointDao
    abstract fun alertEventDao(): AlertEventDao
    abstract fun outgoingEventDao(): OutgoingEventDao
    abstract fun catchLogDao(): CatchLogDao

    companion object {
        @Volatile
        private var INSTANCE: CaptainAviDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): CaptainAviDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CaptainAviDatabase::class.java,
                    "captain_avi.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateDefaultWaypoints(database.waypointDao())
                    }
                }
            }

            private suspend fun populateDefaultWaypoints(dao: WaypointDao) {
                // Demo-only waypoints. Their labels deliberately prevent them being
                // mistaken for surveyed or official navigation information.
                val defaultWaypoints = listOf(
                    WaypointEntity(
                        name = "SAMPLE — Home Island",
                        type = WaypointType.HOME,
                        latitude = 4.1755,
                        longitude = 73.5093,
                        radiusMeters = 500.0,
                        description = "Demo only — replace with your verified home harbour"
                    ),
                    WaypointEntity(
                        name = "SAMPLE — North Harbour Channel",
                        type = WaypointType.HARBOUR,
                        latitude = 4.1802,
                        longitude = 73.5120,
                        radiusMeters = 200.0,
                        description = "Demo only — not a surveyed safe-water channel"
                    ),
                    WaypointEntity(
                        name = "SAMPLE — Tuna Drop-off Point A",
                        type = WaypointType.FISHING_SPOT,
                        latitude = 4.2250,
                        longitude = 73.5480,
                        radiusMeters = 800.0,
                        description = "Demo only — unverified fishing mark"
                    ),
                    WaypointEntity(
                        name = "SAMPLE — Snapper Bank B",
                        type = WaypointType.FISHING_SPOT,
                        latitude = 4.1420,
                        longitude = 73.4750,
                        radiusMeters = 600.0,
                        description = "Demo only — depth and position are unverified"
                    ),
                    WaypointEntity(
                        name = "SAMPLE — Shallow Coral Head",
                        type = WaypointType.DANGER_REEF,
                        latitude = 4.2010,
                        longitude = 73.4920,
                        radiusMeters = 400.0,
                        description = "Demo only — not an official or surveyed hazard"
                    ),
                    WaypointEntity(
                        name = "SAMPLE — South Shoal",
                        type = WaypointType.DANGER_REEF,
                        latitude = 4.1200,
                        longitude = 73.5250,
                        radiusMeters = 500.0,
                        description = "Demo only — not an official or surveyed hazard"
                    )
                )
                dao.insertWaypoints(defaultWaypoints)
            }
        }

        /** Relabel only the exact v1 demo records; user-created marks are not matched. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val demoRecords = listOf(
                    Triple("Home Island", 4.1755, 73.5093),
                    Triple("North Harbour Channel", 4.1802, 73.5120),
                    Triple("Tuna Drop-off Point A", 4.2250, 73.5480),
                    Triple("Snapper Bank B", 4.1420, 73.4750),
                    Triple("DANGEROUS REEF: Shallow Coral Head", 4.2010, 73.4920),
                    Triple("DANGEROUS REEF: South Shoal", 4.1200, 73.5250),
                )
                demoRecords.forEach { (name, latitude, longitude) ->
                    db.execSQL(
                        "UPDATE waypoints SET name = ? WHERE name = ? AND latitude = ? AND longitude = ?",
                        arrayOf(
                            "SAMPLE — ${name.removePrefix("DANGEROUS REEF: ")}",
                            name,
                            latitude,
                            longitude,
                        ),
                    )
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS outgoing_events (
                        id TEXT NOT NULL PRIMARY KEY,
                        tripId TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        attemptCount INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_outgoing_events_tripId ON outgoing_events (tripId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_outgoing_events_timestamp ON outgoing_events (timestamp)",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS catch_logs (
                        id TEXT NOT NULL PRIMARY KEY,
                        tripId TEXT NOT NULL,
                        species TEXT NOT NULL,
                        count INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        latitude REAL,
                        longitude REAL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_catch_logs_tripId ON catch_logs (tripId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_catch_logs_timestamp ON catch_logs (timestamp)",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE catch_logs ADD COLUMN habitat TEXT NOT NULL DEFAULT 'OTHER'",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_catch_logs_habitat ON catch_logs (habitat)",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE waypoints ADD COLUMN targetSpeciesJson TEXT NOT NULL DEFAULT '[]'",
                )
            }
        }
    }
}
