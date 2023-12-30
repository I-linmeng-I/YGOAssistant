package Linmeng.Data.PluginData

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value

object GroupPlayerTags:AutoSavePluginData("GroupPlayerTags") {
    var data:MutableMap<String ,RobotTagData> by value()
}

@kotlinx.serialization.Serializable
data class RobotTagData(
    var RobotTagData:MutableList<String>
)