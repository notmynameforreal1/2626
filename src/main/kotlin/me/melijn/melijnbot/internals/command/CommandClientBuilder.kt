package me.melijn.melijnbot.internals.command

import me.melijn.melijnbot.Container
import me.melijn.melijnbot.internals.arguments.ArgumentInfo
import me.melijn.melijnbot.internals.arguments.CommandArg
import me.melijn.melijnbot.internals.arguments.CommandArgParser
import me.melijn.melijnbot.internals.arguments.MethodArgumentInfo
import me.melijn.melijnbot.internals.threading.TaskManager
import org.jetbrains.kotlin.descriptors.runtime.structure.parameterizedTypeArguments
import org.reflections.Reflections
import org.slf4j.LoggerFactory
import java.lang.reflect.Parameter

class CommandClientBuilder(private val container: Container) {

    private val logger = LoggerFactory.getLogger(this::class.java.name)
    private var commands = HashSet<AbstractCommand>()
    private var commandExecuteMap = HashMap<Class<out AbstractCommand>, MethodArgumentInfo>()
    private var argumentParsers = HashMap<Class<Any>, CommandArgParser<Any>>()

    init {
        logger.info("Loading commands...")
    }

    fun build(): CommandClient {
        loadCommands()
        loadArgumentParsers()
        loadFunctions()

        injectFunctionInfoIntoCommands()

        return CommandClient(commands, commandExecuteMap, argumentParsers, container)
    }

    private fun injectFunctionInfoIntoCommands() {
        commands.forEach { cmd ->
            val methodArgumentInfo = commandExecuteMap[cmd.javaClass]
            if (methodArgumentInfo == null) {
                println("no info for $cmd")
            }
            methodArgumentInfo?.let {
                cmd.selfExecuteInformation = it
            }
        }
    }

    private fun loadArgumentParsers() {
        val reflections = Reflections("me.melijn.melijnbot.internals.arguments.parser")
        val notHash = reflections.getSubTypesOf(CommandArgParser::class.java)
            .map {
                val argParser: CommandArgParser<Any> = it.getConstructor().newInstance() as CommandArgParser<Any>
                val clazz = it.genericSuperclass.parameterizedTypeArguments.first() as Class<Any>
                clazz to argParser
            }.toMap()
        argumentParsers = HashMap(notHash)
    }

    private fun loadFunctions() {
        val reflections = Reflections("me.melijn.melijnbot.commands")
        val filtered = reflections.getSubTypesOf(AbstractCommand::class.java)
            .filter {
                it.methods.any { method ->
                    method.name == "execute"
                }
            }.map {
                val method = it.methods.first { method ->
                    method.name == "execute"
                }

                val methodArgumentParsers = mutableMapOf<Parameter, ArgumentInfo>()
                method.parameters.forEach { param ->
                    val clazz = param.type as Class<Any>

                    val argumentInfo = ArgumentInfo(
                        param.getAnnotation(CommandArg::class.java),
                        null,
                        argumentParsers[clazz]
                    )
                    methodArgumentParsers[param] = argumentInfo
                }
                val methodArgumentInfo = MethodArgumentInfo(method, methodArgumentParsers)

                it to methodArgumentInfo
            }.toMap()



        commandExecuteMap = HashMap(filtered)
        logger.info("Loaded ${filtered.size} executes in commands")
    }

    private fun loadCommands() {
        val reflections = Reflections("me.melijn.melijnbot.commands")
        val filtered = reflections.getSubTypesOf(AbstractCommand::class.java)
            .filter {
                !it.isMemberClass && it.constructors[0].parameterCount == 0
            }

        commands = filtered.map {
            it.getConstructor().newInstance()
        }.toHashSet()

        TaskManager.async {
            container.daoManager.commandWrapper.bulkInsert(commands)
        }

        logger.info("Loaded ${commands.size} commands")
    }
}