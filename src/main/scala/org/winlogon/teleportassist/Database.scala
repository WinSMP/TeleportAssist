// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import org.bukkit.Location

import java.io.File
import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}
import java.util.UUID

case class WarpData(
    name: String,
    world: String,
    x: Double, y: Double, z: Double,
    yaw: Float, pitch: Float,
    owner: Option[UUID],
    isPublic: Boolean,
    createdAt: Long
)

case class DeathLocationData(
    player: UUID,
    world: String,
    x: Double, y: Double, z: Double,
    yaw: Float, pitch: Float,
    timestamp: Long
)

case class SpawnData(
    world: String,
    x: Double, y: Double, z: Double,
    yaw: Float, pitch: Float
)

class Database(dataFolder: File) {
    private var connection: Connection = _
    private val dbFile = File(dataFolder, "teleportassist.db")

    def init(): Unit = {
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection(s"jdbc:sqlite:${dbFile.getAbsolutePath}")
        createTables()
    }

    private def createTables(): Unit = {
        val stmt = connection.createStatement()
        stmt.execute(
            """CREATE TABLE IF NOT EXISTS warps (
                 name TEXT NOT NULL PRIMARY KEY,
                 world TEXT NOT NULL,
                 x REAL NOT NULL,
                 y REAL NOT NULL,
                 z REAL NOT NULL,
                 yaw REAL NOT NULL DEFAULT 0,
                 pitch REAL NOT NULL DEFAULT 0,
                 owner TEXT,
                 is_public INTEGER NOT NULL DEFAULT 1,
                 created_at INTEGER NOT NULL
               )"""
        )
        stmt.execute(
            """CREATE TABLE IF NOT EXISTS death_locations (
                 player TEXT NOT NULL,
                 world TEXT NOT NULL,
                 x REAL NOT NULL,
                 y REAL NOT NULL,
                 z REAL NOT NULL,
                 yaw REAL NOT NULL,
                 pitch REAL NOT NULL,
                 timestamp INTEGER NOT NULL,
                 PRIMARY KEY (player, timestamp)
               )"""
        )
        stmt.execute(
            """CREATE TABLE IF NOT EXISTS spawn_locations (
                 world TEXT NOT NULL PRIMARY KEY,
                 x REAL NOT NULL,
                 y REAL NOT NULL,
                 z REAL NOT NULL,
                 yaw REAL NOT NULL,
                 pitch REAL NOT NULL
               )"""
        )
        stmt.close()
    }

    def close(): Unit = {
        if (connection != null && !connection.isClosed)
            connection.close()
    }

    // Warps

    def createWarp(name: String, loc: Location, owner: Option[UUID] = None): Boolean = {
        val stmt = connection.prepareStatement(
            """INSERT INTO warps (name, world, x, y, z, yaw, pitch, owner, is_public, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        )
        stmt.setString(1, name)
        bindLocation(stmt, 2, loc)
        stmt.setString(8, owner.map(_.toString).orNull)
        stmt.setInt(9, 1)
        stmt.setLong(10, System.currentTimeMillis() / 1000)
        val result = stmt.executeUpdate()
        stmt.close()
        result > 0
    }

    def removeWarp(name: String): Boolean = {
        val stmt = connection.prepareStatement("DELETE FROM warps WHERE name = ?")
        stmt.setString(1, name)
        val result = stmt.executeUpdate()
        stmt.close()
        result > 0
    }

    def getWarp(name: String): Option[WarpData] = {
        val stmt = connection.prepareStatement("SELECT * FROM warps WHERE name = ?")
        stmt.setString(1, name)
        val rs = stmt.executeQuery()
        val result = if (rs.next()) Some(readWarp(rs)) else None
        rs.close(); stmt.close()
        result
    }

    def listWarps(): Seq[String] = {
        val stmt = connection.createStatement()
        val rs = stmt.executeQuery("SELECT name FROM warps ORDER BY name")
        val names = Iterator.continually(rs).takeWhile(_.next()).map(_.getString("name")).toSeq
        rs.close(); stmt.close()
        names
    }

    def getAllWarps(): Seq[WarpData] = {
        val stmt = connection.createStatement()
        val rs = stmt.executeQuery("SELECT * FROM warps ORDER BY name")
        val warps = Iterator.continually(rs).takeWhile(_.next()).map(readWarp).toSeq
        rs.close(); stmt.close()
        warps
    }

    def updateWarp(name: String, loc: Location): Boolean = {
        val stmt = connection.prepareStatement(
            "UPDATE warps SET world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ? WHERE name = ?"
        )
        bindLocation(stmt, 1, loc)
        stmt.setString(7, name)
        val result = stmt.executeUpdate()
        stmt.close()
        result > 0
    }

    private def readWarp(rs: ResultSet): WarpData = WarpData(
        rs.getString("name"),
        rs.getString("world"),
        rs.getDouble("x"),
        rs.getDouble("y"),
        rs.getDouble("z"),
        rs.getFloat("yaw"),
        rs.getFloat("pitch"),
        Option(rs.getString("owner")).map(UUID.fromString),
        rs.getInt("is_public") == 1,
        rs.getLong("created_at")
    )

    // Death Locations

    def saveDeathLocation(player: UUID, loc: Location): Unit = {
        val stmt = connection.prepareStatement(
            """INSERT OR REPLACE INTO death_locations (player, world, x, y, z, yaw, pitch, timestamp)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)"""
        )
        stmt.setString(1, player.toString)
        bindLocation(stmt, 2, loc)
        stmt.setLong(8, System.currentTimeMillis() / 1000)
        stmt.executeUpdate()
        stmt.close()
    }

    def getLatestDeathLocation(player: UUID): Option[DeathLocationData] = {
        val stmt = connection.prepareStatement(
            "SELECT * FROM death_locations WHERE player = ? ORDER BY timestamp DESC LIMIT 1"
        )
        stmt.setString(1, player.toString)
        val rs = stmt.executeQuery()
        val result = if (rs.next()) Some(DeathLocationData(
            UUID.fromString(rs.getString("player")),
            rs.getString("world"),
            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
            rs.getFloat("yaw"), rs.getFloat("pitch"),
            rs.getLong("timestamp")
        )) else None
        rs.close(); stmt.close()
        result
    }

    // Spawn Locations

    def setSpawn(loc: Location): Unit = {
        val stmt = connection.prepareStatement(
            """INSERT OR REPLACE INTO spawn_locations (world, x, y, z, yaw, pitch)
               VALUES (?, ?, ?, ?, ?, ?)"""
        )
        bindLocation(stmt, 1, loc)
        stmt.executeUpdate()
        stmt.close()
    }

    def getSpawn(worldName: String): Option[SpawnData] = {
        val stmt = connection.prepareStatement("SELECT * FROM spawn_locations WHERE world = ?")
        stmt.setString(1, worldName)
        val rs = stmt.executeQuery()
        val result = if (rs.next()) Some(SpawnData(
            rs.getString("world"),
            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
            rs.getFloat("yaw"), rs.getFloat("pitch")
        )) else None
        rs.close(); stmt.close()
        result
    }

    def isConnected: Boolean =
        connection != null && !connection.isClosed

    private def bindLocation(stmt: PreparedStatement, idx: Int, loc: Location): Unit = {
        stmt.setString(idx, loc.getWorld.getName)
        stmt.setDouble(idx + 1, loc.getX)
        stmt.setDouble(idx + 2, loc.getY)
        stmt.setDouble(idx + 3, loc.getZ)
        stmt.setFloat(idx + 4, loc.getYaw)
        stmt.setFloat(idx + 5, loc.getPitch)
    }
}
