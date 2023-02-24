package Linmeng

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value

object Config:AutoSavePluginConfig("config") {
    val admin: MutableList<Long> by value()
}