package me.melijn.melijnbot.database.permissions

import me.melijn.melijnbot.database.Dao
import me.melijn.melijnbot.database.DriverManager
import me.melijn.melijnbot.enums.PermState
import java.util.function.Consumer

class RolePermissionDao(private val driverManager: DriverManager) : Dao(driverManager) {

    override val table: String = "rolePermissions"
    override val tableStructure: String = "guildId bigint, roleId bigint, permission varchar(64), state varchar(8)"
    override val keys: String = "UNIQUE KEY (roleId, permission)"

    init {
        driverManager.registerTable(table, tableStructure, keys)
    }

    fun getPermState(roleId: Long, permission: String, permState: (PermState) -> Unit) {
        driverManager.executeQuery("SELECT * FROM $table WHERE roleId = ? AND permission = ?", { resultset ->
            if (resultset.next()) {
                permState.invoke(PermState.valueOf(resultset.getString("state")))
            } else permState.invoke(PermState.DEFAULT)
        }, roleId, permission)
    }

    fun set(guildId: Long, roleId: Long, permission: String, permState: PermState) {
        driverManager.executeUpdate("INSERT INTO $table (guildId, roleId, permission, state) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE state = ?",
                guildId, roleId, permission, permState.toString(), permState.toString())
    }

    fun delete(roleId: Long, permission: String) {
        driverManager.executeUpdate("DELETE FROM $table WHERE roleId = ? AND permission = ?", roleId, permission)
    }

    fun delete(roleId: Long) {
        driverManager.executeUpdate("DELETE FROM $table WHERE roleId = ?", roleId)
    }

    fun getMap(roleId: Long, permStateMap: (Map<String, PermState>) -> Unit) {
        driverManager.executeQuery("SELECT * FROM $table WHERE roleId = ?", { resultset ->
            val map = HashMap<String, PermState>()
            while (resultset.next()) {
                map[resultset.getString("permission")] = PermState.valueOf(resultset.getString("state"))
            }
            permStateMap.invoke(map)
        }, roleId)
    }

    fun bulkPut(guildId: Long, roleId: Long, permissions: List<String>, state: PermState) {
        driverManager.getUsableConnection { connection ->
            connection.prepareStatement("INSERT INTO $table (guildId, roleId, permission, state) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE state = ?").use { statement ->
                statement.setLong(1, guildId)
                statement.setLong(2, roleId)
                statement.setString(4, state.toString())
                statement.setString(5, state.toString())
                for (perm in permissions) {
                    statement.setString(3, perm)
                    statement.addBatch()
                }
                statement.executeLargeBatch()
            }
        }
    }

    fun bulkDelete(roleId: Long, permissions: List<String>) {
        driverManager.getUsableConnection { connection ->
            connection.prepareStatement("DELETE FROM $table WHERE roleId = ? AND permission = ?").use { statement ->
                statement.setLong(1, roleId)
                for (perm in permissions) {
                    statement.setString(2, perm)
                    statement.addBatch()
                }
                statement.executeLargeBatch()
            }
        }
    }
}