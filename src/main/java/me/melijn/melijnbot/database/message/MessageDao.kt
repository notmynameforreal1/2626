package me.melijn.melijnbot.database.message

import me.melijn.melijnbot.database.Dao
import me.melijn.melijnbot.database.DriverManager
import me.melijn.melijnbot.enums.MessageType
import net.dv8tion.jda.api.entities.MessageEmbed
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MessageDao(driverManager: DriverManager) : Dao(driverManager) {

    override val table: String = "messages"
    override val tableStructure: String = "guildId bigint, type varchar(32), message varchar(2048)"
    override val keys: String = "UNIQUE KEY(guildId, type)"

    init {
        driverManager.registerTable(table, tableStructure, keys)
    }

    fun set(guildId: Long, type: MessageType, message: String) {
        driverManager.executeUpdate("INSERT INTO $table (guildId, type, message) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE message = ?",
                guildId, type.toString(), message, message)
    }

    suspend fun get(guildId: Long, type: MessageType): String = suspendCoroutine {
        driverManager.executeQuery("SELECT * FROM $table WHERE guildId = ? AND type = ?", { rs ->
            if (rs.next()) {
                it.resume(rs.getString("content"))
            } else it.resume("")
        }, guildId, type.toString())
    }

    fun remove(guildId: Long, type: MessageType) {
        driverManager.executeUpdate("REMOVE FROM $table WHERE guildId = ? AND type = ?",
                guildId, type.toString())
    }
}

data class ModularMessage(val messageContent: String? = null,
                          val embed: MessageEmbed? = null,
                          val attachments: List<String> = emptyList()) {
    fun toJSON(): String {
        val json = JSONObject()
        messageContent?.let { json.put("content", it) }
        embed?.let {
            val embedJson = JSONObject()
            embedJson.put("title", )

            json.put("embed", embedJson)
        }
        attachments.let {
            json.put("attachments", it.joinToString("%SPLIT%"))
        }
        return json.toString(4)
    }
}