package me.melijn.melijnbot.internals.models

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import me.melijn.melijnbot.MelijnBot
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import java.time.Instant
import java.util.*


data class ModularMessage(
    var messageContent: String? = null,
    var embed: MessageEmbed? = null,
    var attachments: Map<String, String> = emptyMap(), // url -> name
    var extra: Map<String, String> = emptyMap()
) {

    @JsonValue
    fun toJSON(): String {
        val json = DataObject.empty()
        messageContent?.let { json.put("content", it) }
        embed?.let { membed ->
            json.put(
                "embed", membed.toData()
                    .put("type", "RICH")
            )
        }

        val attachmentsJson = DataArray.empty()
        for ((key, value) in attachments) {
            attachmentsJson.add(
                DataObject.empty()
                    .put("url", key)
                    .put("file", value)
            )
        }
        json.put("attachments", attachmentsJson)

        val extraJson = DataArray.empty()
        for ((key, value) in extra) {
            extraJson.add(
                DataArray.empty()
                    .add(key)
                    .add(value)
            )
        }
        json.put("extra", extraJson)
        return json.toString()
    }

    fun toMessage(): Message? {
        var membed = embed
        if (messageContent == null && (membed == null || membed.isEmpty || !membed.isSendable)) return null


        // Timestamp handler
        if (membed != null && extra.containsKey("currentTimestamp")) {
            membed = EmbedBuilder(membed)
                .setTimestamp(Instant.now())
                .build()
        }

        val mb = MessageBuilder()
            .setEmbed(membed)
            .setContent(messageContent)

        // Timestamp handler
        if (extra.containsKey("isPingable")) {
            mb.setAllowedMentions(
                EnumSet.allOf(Message.MentionType::class.java) // Default to all mentions enabled
            )
        } else {
            mb.setAllowedMentions(emptyList())
        }

        return try {
            mb.build()
        } catch (t: IllegalStateException) { // Fixes: Cannot build a Message with no content
            mb.setContent("This message had no content. (This is placeholder text for empty messages)")
                .build()
        }
    }

    suspend fun mapAllStringFields(function: suspend (s: String?) -> String?): ModularMessage {
        val mappedModularMsg = ModularMessage()
        mappedModularMsg.messageContent = this.messageContent?.let { function(it) }

        this.embed?.let { embed ->
            val mappedEmbed = EmbedBuilder()
            embed.title?.let {
                mappedEmbed.setTitle(function(it), function(embed.url))
            }
            embed.description?.let {
                mappedEmbed.setDescription(function(it))
            }
            embed.author?.let {
                mappedEmbed.setAuthor(function(it.name), function(it.url), function(it.iconUrl))
            }
            embed.footer?.let {
                mappedEmbed.setFooter(function(it.text), function(it.iconUrl))
            }
            embed.image?.let {
                val function1 = function(it.url)
                mappedEmbed.setImage(function1)
            }
            embed.thumbnail?.let {
                mappedEmbed.setThumbnail(function(it.url))
            }
            embed.fields.forEach { field ->
                val name = function(field.name)
                val value = function(field.value)
                if (name != null && value != null)
                    mappedEmbed.addField(name, value, field.isInline)
            }
            mappedEmbed.setColor(embed.color)
            mappedEmbed.setTimestamp(embed.timestamp)

            mappedModularMsg.embed = mappedEmbed.build()
        }

        val mappedAttachments = mutableMapOf<String, String>()
        this.attachments.forEach { entry ->
            function(entry.value)?.let { mappedValue ->
                function(entry.key)?.let { mappedKey ->
                    mappedAttachments[mappedKey] = mappedValue
                }
            }
        }
        mappedModularMsg.attachments = mappedAttachments
        mappedModularMsg.extra = extra

        return mappedModularMsg
    }

    companion object {
        fun fromJSON(json: String): ModularMessage {
            try {
                val jsonObj = DataObject.fromJson(json)

                // Just text
                var content: String? = null
                if (jsonObj.hasKey("content")) {
                    content = jsonObj.getString("content")
                }

                // Embed
                var embed: MessageEmbed? = null
                if (jsonObj.hasKey("embed")) {
                    val jdaImpl = (MelijnBot.shardManager.shards[0] as JDAImpl)
                    val embedString = jsonObj.getObject("embed")
                    val dataObject = DataObject.fromJson(embedString.toString())
                    embed = jdaImpl.entityBuilder.createMessageEmbed(dataObject)
                }

                // Attachments
                val attachments = mutableMapOf<String, String>()
                if (jsonObj.hasKey("attachments")) {
                    val attachmentsJson = jsonObj.getArray("attachments")

                    for (i in 0 until attachmentsJson.length()) {
                        val attachmentObj = attachmentsJson.getObject(i)
                        attachments[attachmentObj.getString("url")] = attachmentObj.getString("file")
                    }
                }

                // Extra data
                val extra = mutableMapOf<String, String>()
                if (jsonObj.hasKey("extra")) {
                    val extraJson = jsonObj.getArray("extra")
                    for (i in 0 until extraJson.length()) {
                        val extraObj = extraJson.getArray(i)
                        extra[extraObj.getString(0)] = extraObj.getString(1)
                    }
                }
                return ModularMessage(content, embed, attachments, extra)
            } catch (e: Exception) {
                e.printStackTrace()
                throw IllegalArgumentException("Invalid JSON structure")
            }
        }
    }


    class ModularMessageDeserializer : StdDeserializer<ModularMessage>(ModularMessage::class.java) {

        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ModularMessage {
            return fromJSON(p.text)
        }
    }
}