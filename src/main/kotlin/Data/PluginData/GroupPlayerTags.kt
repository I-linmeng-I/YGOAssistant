package Linmeng.Data.PluginData

import Linmeng.GroupScribtion.provideDelegate
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import java.net.URL
import java.net.URLEncoder

object GroupPlayerTags:AutoSavePluginData("GroupPlayerTags") {
    var data:MutableMap<String ,MutableList<String>> by value()
}
