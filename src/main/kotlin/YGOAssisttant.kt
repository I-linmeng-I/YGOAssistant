package Linmeng

import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.utils.info

object YGOAssisttant : KotlinPlugin(
    JvmPluginDescription(
        id = "com.linmeng.YGOAssisttant",
        name = "YGOAssisttant",
        version = "0.1.0",
    ) {
        author("linmeng")
    }
) {
    override fun onEnable() {
        logger.info { "Plugin loaded" }
    }
}