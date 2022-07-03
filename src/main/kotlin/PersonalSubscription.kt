package Linmeng

import Linmeng.GroupScribtion.provideDelegate
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import java.net.URL
import java.net.URLEncoder

object PersonalSubscription: AutoSavePluginData("PersonalScribtionData") {
    var data:MutableMap<Long,PersonalData> by value()
}

@kotlinx.serialization.Serializable
data class PersonalData(
    var SubscribedUser:MutableList<String>
){
    fun GetWebSourceCode(url:String):String{

        val doc = URL(url).readText()
        return doc
    }


    fun AddSubscribeUser(playerName:String):String{
        val index = SubscribedUser.indexOfFirst { it ==  playerName}
        if (index != -1) return "已经订阅这个人了"

        val playerNameInURL = URLEncoder.encode( playerName,"UTF-8")

        //获取网页数据
        var WebData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/user?username=${playerNameInURL}")

        val  RatioMatch = Regex(""""athletic_wl_ratio":"(.*?)","arena""").find(WebData)?:return "就没这人"
        val  Ratio= RatioMatch.groupValues[1]

        if (Ratio == "0"){
            return "就没这人"
        }

        SubscribedUser.add(playerName)

        return "订阅成功，啊，可以对"+playerName+"进行及时的指指点点了"
    }

    fun DissubscribeUser(playerName:String):String{
        val index = SubscribedUser.indexOfFirst { it ==  playerName}
        if (index == -1){
            return "你还没订阅这个人"
        }
        else{
            SubscribedUser.removeAt(index)
        }
        return "取消订阅成功，你现在没法对"+playerName+"进行及时的指指点点了"
    }
}
