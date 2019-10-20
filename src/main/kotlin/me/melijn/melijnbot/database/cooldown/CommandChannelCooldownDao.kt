package me.melijn.melijnbot.database.cooldown

import me.melijn.melijnbot.database.Dao
import me.melijn.melijnbot.database.DriverManager

class CommandChannelCooldownDao(driverManager: DriverManager) : Dao(driverManager) {
    override val table: String = "commandChannelCooldowns"
    override val tableStructure: String = "guildId bigint UNIQUE, channelId bigint, commandId varchar(16) UNIQUE, cooldownMillis bigint"
    override val keys: String = ""

    init {
        driverManager.registerTable(table, tableStructure, keys)
    }

    fun getCooldownMapForChannel(channelId: Long, cooldownMap: (Map<String, Long>) -> Unit) {
        driverManager.executeQuery("SELECT * FROM $table WHERE channelId = ?", {
            val map = HashMap<String, Long>()
            while (it.next()) {
                map[it.getString("commandId")] = it.getLong("cooldownMillis")
            }
            cooldownMap.invoke(map)
        }, channelId)
    }

    fun bulkPut(guildId: Long, channelId: Long, commandsIds: Set<String>, cooldownMillis: Long) {
        driverManager.getUsableConnection { con ->
            con.prepareStatement("INSERT INTO $table (guildId, channelId, commandId, cooldownMillis) VALUES (?, ?, ?, ?) ON CONFLICT (guildId, commandId) DO UPDATE cooldownMillis = ?").use {
                preparedStatement ->
                preparedStatement.setLong(1, guildId)
                preparedStatement.setLong(2, channelId)
                preparedStatement.setLong(4, cooldownMillis)
                preparedStatement.setLong(5, cooldownMillis)
                for (cmdId in commandsIds) {
                    preparedStatement.setString(3, cmdId)
                    preparedStatement.addBatch()
                }
                preparedStatement.executeLargeBatch()
            }
        }
    }

    fun bulkDelete(channelId: Long, commandsIds: Set<String>) {
        driverManager.getUsableConnection { con ->
            con.prepareStatement("DELETE FROM $table WHERE channelId = ? AND commandId = ?").use {
                preparedStatement ->
                preparedStatement.setLong(1, channelId)
                for (cmdId in commandsIds) {
                    preparedStatement.setString(2, cmdId)
                    preparedStatement.addBatch()
                }
                preparedStatement.executeLargeBatch()
            }
        }
    }
}