package me.melijn.melijnbot.objects.services.donator

import me.melijn.melijnbot.Container
import me.melijn.melijnbot.objects.services.Service
import me.melijn.melijnbot.objects.threading.Task
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.sharding.ShardManager
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class DonatorService(val container: Container, val shardManager: ShardManager) : Service("donator") {


    private var scheduledFuture: ScheduledFuture<*>? = null

    private val donatorService = Task {
        val wrapper = container.daoManager.supporterWrapper
        val guild = shardManager.getGuildById(340081887265685504)

        for (member in guild?.memberCache ?: emptyList<Member>()) {
            val isPremium = member.roles.any {
                it.idLong == 488579500427313208 || it.idLong == 686243026384715796
            }
            if (isPremium) {
                wrapper.add(member.idLong)

            } else {
                wrapper.remove(member.idLong)
            }
        }
    }

    override fun start() {
        logger.info("Started DonatorService")
        scheduledFuture = scheduledExecutor.scheduleWithFixedDelay(donatorService, 2, 2, TimeUnit.MINUTES)
    }

    override fun stop() {
        logger.info("Stopping DonatorService")
        scheduledFuture?.cancel(false)
    }
}