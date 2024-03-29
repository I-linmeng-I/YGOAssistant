package Linmeng


import Linmeng.Data.PluginData.GroupPlayerTags
import Linmeng.Data.PluginData.RobotTagData
import Linmeng.YGOAssisttant.logger
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.console.util.ContactUtils.getContactOrNull
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.utils.info
import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartUtils
import org.jfree.chart.block.BlockBorder
import org.jfree.chart.labels.StandardPieSectionLabelGenerator
import org.jfree.chart.plot.*
import org.jfree.chart.ui.HorizontalAlignment
import org.jfree.chart.ui.RectangleEdge
import org.jfree.chart.ui.RectangleInsets
import org.jfree.data.general.DefaultPieDataset
import org.jfree.data.general.PieDataset
import java.awt.Font
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.StringReader
import java.lang.Double.parseDouble
import java.math.RoundingMode
import java.net.URL
import java.net.URLEncoder
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.time.LocalDate




class Command {

    var UserSearchDataList =  hashSetOf<UserSearchData>()
    val duelList = mutableListOf<DuelInfo>()



    private val loginclient = HttpClient(OkHttp) {
        // when set to true (by default), an exception would thrown if the response http status code
        // is not in 200-300
        expectSuccess = false
        install(HttpTimeout) {
            socketTimeoutMillis = 5000
        }
    }

    fun SearchTag(playerName: String): String{
        val tagData = GroupPlayerTags.data
        var returnMsg = ""
        if(tagData[playerName] !=null ){
            if(tagData[playerName]?.RobotTagData?.isNotEmpty() == true){
                tagData[playerName]?.RobotTagData?.forEach{
                    returnMsg += " #${it}"
                }
            }
        }
        return returnMsg
    }

    suspend fun login(username: String, password: String): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val ret: String = loginclient.submitForm(
                    "https://api.moecube.com/accounts/" + "signin",
                    Parameters.build {
                        append("account", username)
                        append("password", password)
                    }, false
                ).bodyAsText()

                if(ret!=""){
                    return@runCatching ret
                }
                else{
                    return@runCatching ret
                }
            }
        }.toString()

    fun generateToken(id: Int): String {
        val b = arrayOf(0xD0, 0x30, 0, 0, 0, 0)
        val r = id % 0xFFFF + 1
        for (t in b.indices step 2) {
            val k = b[t + 1] shl 8 or b[t] xor r
            b[t] = k and 0xFF
            b[t + 1] = k ushr 8 and 0xFF
        }
        return Base64.getEncoder().encodeToString(ByteArray(b.size) { i -> (b[i] and 0xFF).toByte() })//.encode(ByteArray(b.size) { i -> (b[i] and 0xFF).toByte() }).toString()
    }

    //排序
    fun GetSmaller(num1:Int,num2:Int):Int{
        return if(num1>num2){
            num2
        } else{
            num1
        }
    }

    fun partition(li:MutableList<DuelInfo>, left:Int, right:Int):Int{
        var leftin = left
        var rightin = right
        val tmp = GetSmaller(li[leftin].player1Rank.toInt(),li[leftin].player2Rank.toInt())
        val tmpDuelInfo = li[leftin]
        while (leftin<rightin){
            while (leftin<rightin && GetSmaller(li[rightin].player1Rank.toInt(),li[rightin].player2Rank.toInt())>=tmp){
                rightin -= 1
            }
            li[leftin]=li[rightin]
            while (leftin<rightin && GetSmaller(li[leftin].player1Rank.toInt(),li[leftin].player2Rank.toInt())<=tmp){
                leftin += 1
            }
            li[rightin] = li[leftin]
        }
        li[leftin] = tmpDuelInfo
        return leftin
    }

    fun QuickSort(li:MutableList<DuelInfo>, left:Int, right:Int){
        val leftin = left
        val rightin = right
        if(leftin<rightin){
            val mid = partition(li,leftin,rightin)
            QuickSort(li,leftin,mid-1)
            QuickSort(li,mid+1,right)
        }
    }

    //敏感词匹配
    fun WordsMatch(word:String):String{
        val file = File("./data/YGOAssistant/")
        val file1 = File("./data/YGOAssistant/WordsMatchLibrary.txt")
        if (!file.exists()) file.mkdirs()
        if (!file1.exists()){
            //val filePath = "a.txt"
            file1.appendText("")
        }
        val words = file1.readLines()

        words.forEach{
            val tempText = it
            val index = word.indexOf(tempText)
            if (index != -1) return "敏感词玩家"
        }
        return word
    }

    //计算两个时间加减
    fun CalculateTime(startTime:String,endTime:String):String{
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

        val sTime: Date = dateFormat.parse(startTime)
        val eTime: Date = dateFormat.parse(endTime)
        println("eTime=$eTime")
        val diff = eTime.time - sTime.time
        val days = diff / (1000 * 60 * 60 * 24)
        val hours = (diff - days * (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
        val minutes = ((diff - days * (1000 * 3600 * 24)) - hours * (1000 * 3600)) / (1000 * 60)
        val second = (diff - days * 1000 * 3600 * 24 - hours * 1000 * 3600 - minutes * 1000 * 60) / 1000
        return if(hours.toInt()==0){
            "$minutes:$second"
        } else{
            "$hours:$minutes:$second"
        }
    }

    //计算时间加小时
    fun addEightHours(input: String,addTime: Long): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val dateTime = LocalDateTime.parse(input, formatter)
        val newDateTime = dateTime.plusHours(addTime)
        return newDateTime.format(formatter)
    }
    //是不是数字
    fun IsNumber(args: String):Boolean {

        val string = args
        var numeric = true

        try {
            parseDouble(string)
        } catch (e: NumberFormatException) {
            numeric = false
        }

        return numeric
    }
    //更新用户数据列表
    fun UpdateUserList(userID: Long):UserSearchData{
        //寻找是否正在查询之前的卡片
        UserSearchDataList.forEach{
            if(it.UesrQQID==userID){
                val userSearchData =it
                UserSearchDataList.remove(it)
                //返回修改完成
                return userSearchData
            }
        }
        //创建用户数据
        val userSearchData = UserSearchData()
        userSearchData.UesrQQID = userID
        userSearchData.UserSearchContent =""
        userSearchData.UserSearchProcess = 0
        userSearchData.UserSearchPage = 1

        return userSearchData
    }


    //连接MC和接收处理数据
    private var client = HttpClient(OkHttp) {
        BrowserUserAgent()
        install(WebSockets) {
            pingInterval = 1000
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 1000
        }
    }





    @net.mamoe.mirai.console.util.ConsoleExperimentalApi
    suspend fun LinkStart() {

        runCatching{
            client.wss(host = "tiramisu.mycard.moe", port = 8923, path = "?filter=started") {
                while (isActive) {
                    //val current = LocalDateTime.now()

                    //val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    //val formatted = current.format(formatter)
                    //YGOAssisttant.logger.info { formatted }
                    val test = withTimeoutOrNull( 600000) {
                        (incoming.receive() as? Frame.Text)?.parse()
                    }.toString()
                    if(test == "null"){
                        YGOAssisttant.logger.error("长时间无接收数据，开始尝试重新连接")
                        client.close()
                        restart()
                    }
                }
            }

        }
    }

    @net.mamoe.mirai.console.util.ConsoleExperimentalApi
    private suspend fun restart(){
        duelList.clear()
        client = HttpClient(OkHttp) {
            BrowserUserAgent()
            install(WebSockets) {
                pingInterval = 1000
            }
            install(HttpTimeout) {
                socketTimeoutMillis = 1000
            }
        }
        LinkStart()
        delay(5000)
        if(duelList.isEmpty()){
            client.close()
            client = HttpClient(OkHttp) {
                BrowserUserAgent()
                install(WebSockets) {
                    pingInterval = 1000
                }
                install(HttpTimeout) {
                    socketTimeoutMillis = 1000
                }
            }
            Bot.instances.forEach { bot ->
                Config.admin.forEach{
                    val subject = bot.getContactOrNull(it)
                    if (subject != null) {
                        subject.sendMessage("重启失败，请手动尝试")
                    }
                    return
                }
            }
        }
        YGOAssisttant.logger.info { "重启成功" }
    }

    //获得萌卡房间相关
    @OptIn(DelicateCoroutinesApi::class)
    @net.mamoe.mirai.console.util.ConsoleExperimentalApi
    private suspend fun Frame.Text.parse() {


        val root = readText()

        val eventRegex = Regex(""""event":"(.*?)","""").find(root)

        if(eventRegex!=null){

            //            if (client.isActive) client.cancel()
            logger.info { "接收到MC对局数据 tpye="+ eventRegex.groupValues[1]}
            when (eventRegex.groupValues[1]) {
                "init" -> {

                    val ResultMatch = Regex(""""id":"(.*?)",".*?"username":"(.*?)",".*?"username":"(.*?)","pos""").findAll(root).toList()


                    if(ResultMatch.isNotEmpty()){
                        ResultMatch.forEach{
                            val duelInfo = DuelInfo()
                            duelInfo.id = it.groupValues[1]
                            duelInfo.Player1 = it.groupValues[2]
                            duelInfo.Player2 = it.groupValues[3]

                            duelInfo.GetPlayerInfo()

                            duelList.add(duelInfo)
                        }
                    }

                    logger.info {"初始化对局列表完成"}
                }
                "delete" ->{
                    val idRegex = Regex("""data":"(.*?)"}""").find(root)
                    if(idRegex!=null){
                        val id = idRegex.groupValues[1]
                        val index = duelList.indexOfFirst { it.id == id }
                        if (index == -1) return
                        duelList.removeAt(index)
                    }

                }
                "create" ->{
                    val ResultMatch = Regex(""""id":"(.*?)",".*?"username":"(.*?)",".*?"username":"(.*?)","pos""").find(root)

                    if(ResultMatch!=null){

                        val duelInfo = DuelInfo()
                        duelInfo.id = ResultMatch.groupValues[1]
                        duelInfo.Player1 = ResultMatch.groupValues[2]
                        duelInfo.Player2 = ResultMatch.groupValues[3]

                        val current = LocalDateTime.now()

                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        val formatted = current.format(formatter)

                        duelInfo.StartTime = formatted
                        duelInfo.GetPlayerInfo()

                        val player1 = duelInfo.Player1
                        val player2 = duelInfo.Player2
                        val duelID = duelInfo.id

                        //个人推送
                        PersonalSubscription.data.forEach{ it ->
                            GlobalScope.launch{
                                val userid= it.key
                                val playerToken=it.value.PlayerToken
                                val playerName = it.value.PlayerName

                                var index = it.value.SubscribedUser.indexOfFirst { it ==  player1}

                                if (index == -1){
                                    index = it.value.SubscribedUser.indexOfFirst { it ==  player2}
                                }
                                else{
                                    //var tagData =  GroupPlayerTags.data.getOrPut(GroupID){ TagGroupData(mutableMapOf<String,MutableList<String>>()) }
                                    Bot.instances.forEach {bot ->

                                        val subject = bot.getContactOrNull(userid)
                                        if(subject!=null){
                                            val tagA=SearchTag(player1)
                                            var returnMsg="你订阅的玩家：" + WordsMatch(player1) + tagA

//                                            if(tagData.TagPlayerAndTag[player1] !=null ){
//                                                if(tagData.TagPlayerAndTag[player1]?.isNotEmpty() == true){
//                                                    tagData.TagPlayerAndTag[player1]?.forEach{
//                                                        returnMsg += " #$it"
//                                                    }
//                                                }
//                                            }
                                            val tagB=SearchTag(player2)
                                            returnMsg += " \n开始了和\n" + WordsMatch(player2) + tagB


//                                            if(tagData.TagPlayerAndTag[player2] !=null ){
//                                                if(tagData.TagPlayerAndTag[player2]?.isNotEmpty() == true){
//                                                    tagData.TagPlayerAndTag[player2]?.forEach{
//                                                        returnMsg += " #$it"
//                                                    }
//                                                }
//                                            }

                                            returnMsg += " 的对战"
                                            if(playerToken!="0"&& player1 != playerName){
                                                returnMsg += "\n观战服务器为：tiramisu.mycard.moe\n端口为：8911\n观战房间密码为：$playerToken$duelID"
                                            }
                                            subject.sendMessage(returnMsg)
                                        }

                                    }
                                    return@launch
                                }

                                if(index != -1){
                                    Bot.instances.forEach {bot ->


                                        val subject = bot.getContactOrNull(userid)
                                        if(subject!=null&& player2 != playerName){
                                            val tagA=SearchTag(player1)
                                            val tagB=SearchTag(player2)
                                            var returnMsg="你订阅的玩家：" + WordsMatch(player2) +tagB + " \n开始了和\n" + WordsMatch(player1) +tagA+ " 的对战"
                                            if(playerToken!="0"){
                                                returnMsg += "\n\n\n观战服务器为：tiramisu.mycard.moe\n端口为：8911\n观战房间密码为：$playerToken$duelID"
                                            }
                                            subject.sendMessage(returnMsg)
                                        }

                                    }
                                }
                            }
                        }


                        duelList.add(duelInfo)

                    }
                }
                else ->{
                    logger.info {root}
                }
            }
        }

    }


    //获取网页数据函数
    fun GetWebSourceCode(url:String):String{

        val doc = URL(url).readText()
        return doc
    }


    //单卡信息
    fun SearchCardInfo(ResultMatch: List<MatchResult>,cardNumber: Int):String{
        if(ResultMatch.size<cardNumber){
            return "已经是最后一张了"
        }
        val CardData2 = GetWebSourceCode("https://ygocdb.com/card/"+ResultMatch[cardNumber-1].groupValues[1])

        val avail = Regex("""OCG</i>([\s\S]*?)TCG</i>([\s\S]*?)</div>""").find(CardData2)?:return  "获得禁限信息失败"
        val ocgAvailMatch: String
        val tcgAvailMatch: String

        if(avail.groupValues[1].indexOf("未发售")==-1){
            ocgAvailMatch = if(avail.groupValues[1].indexOf("禁止卡")>-1){
                "禁止卡"
            } else if (avail.groupValues[1].indexOf("限制卡")>-1){
                if(avail.groupValues[1].indexOf("准限制卡")>-1){
                    "准限制卡"
                } else{
                    "限制卡"
                }
            } else{
                "无限制"
            }
        }
        else{
            ocgAvailMatch = "未在ocg发售"
        }

        if(avail.groupValues[2].indexOf("未发售")==-1){
            tcgAvailMatch = if(avail.groupValues[2].indexOf("禁止卡")>-1){
                "禁止卡"
            } else if (avail.groupValues[2].indexOf("限制卡")>-1){
                if(avail.groupValues[2].indexOf("准限制卡")>-1){
                    "准限制卡"
                } else{
                    "限制卡"
                }
            } else{
                "无限制"
            }
        }
        else{
            tcgAvailMatch = "未在tcg发售"
        }


        val CardData = GetWebSourceCode("https://ygocdb.com/api/v0/?search="+ResultMatch[cardNumber-1].groupValues[1])

        val PEffectMatch = Regex(""""pdesc":"(.*?)","desc":"""").find(CardData)
        var PEffect = ""
        if(PEffectMatch!=null){
            PEffect = PEffectMatch.groupValues[1]
        }

        val CardEffectMatch = Regex("""","desc":"(.*?)"},"data"""").find(CardData)?:return "error"
        val cardEffect=CardEffectMatch.groupValues[1]

        val CardJapanessMatch = Regex("""jp_name":"(.*?)","""").find(CardData)?:return "error"
        val cardJapaness=CardJapanessMatch.groupValues[1]

        var outPutResult=""
        outPutResult += "中文名："+ ResultMatch[cardNumber-1].groupValues[2]+"\n日文名："+cardJapaness+"\n类型："


        val monsterMatch = Regex("""(.*?)<br>(.*)""").find(ResultMatch[cardNumber-1].groupValues[3])
        outPutResult += if(monsterMatch == null){
            ResultMatch[cardNumber-1].groupValues[3]+"\n"
        } else{
            monsterMatch.groupValues[1]+"\n身板："+ monsterMatch.groupValues[2]+"\n"
        }
        outPutResult+="卡片ID："+ResultMatch[cardNumber-1].groupValues[1]

        outPutResult+= "\nOCG禁限情况："+ocgAvailMatch+"\nTCG禁限情况：" +tcgAvailMatch+ "\n" //+ "{分割多段}"

        if(PEffect!=""){
            outPutResult+="灵摆效果："
            if(PEffect.indexOf("\\r")>-1){
                val effect = PEffect.split("\\r\\n")
                effect.forEach{
                    outPutResult+=it+"\n"
                }
            }
            else{
                val effect = PEffect.split("\\n")
                effect.forEach{
                    outPutResult+=it+"\n"
                }
            }
            outPutResult+="怪兽效果：\n"
        }
        else{
            outPutResult+="效果或描述：\n"
        }
        if(cardEffect.indexOf("\\r")>-1){
            val effect = cardEffect.split("\\r\\n")
            effect.forEach{
                outPutResult+=it+"\n"
            }
        }
        else{
            val effect = cardEffect.split("\\n")
            effect.forEach{
                outPutResult+=it+"\n"
            }
        }

        outPutResult += "{加入图片}：url：${ResultMatch[cardNumber-1].groupValues[1]}"

        return outPutResult
    }

    //哎，不写了，反正啊，该看不懂还是看不懂，写那么多都是放屁
    fun AdditionalCommandProcess(cardNumber: String,additionalInfo: String):String{
        var returnMsg = "null"


        if(additionalInfo == "md卡包"){
            val WebData = GetWebSourceCode("https://www.ourocg.cn/search/"+cardNumber)
            val ResultMatch = Regex("""<tr><td><a href="/md_package/(\d*)\D*?>(.*?)<br/><small>(.*?)</small></a></td><td style="text-align:center">(.*?)</td></tr>""").findAll(WebData).toList()
            if (ResultMatch.isNotEmpty()){
                returnMsg = "MD收录卡包:"
                ResultMatch.forEach{
                    returnMsg +="\n卡包编号:"+it.groupValues[1]+ " 日文名:"+it.groupValues[2]+" 英文名:"+it.groupValues[3]+" 罕贵度:"+it.groupValues[4]
                }
            }
        }

        if(additionalInfo == "ocg收录"){
            val WebData = GetWebSourceCode("https://ygocdb.com/card/"+cardNumber)

            val ResultMatch = Regex("""<li class="pack">[\s\S]*?<span>(.*?)</span><span>(.*?)</span>[\s\S]*?<a href="[\s\S]*?">(.*?)</a>""").findAll(WebData).toList()
            if (ResultMatch.isNotEmpty()){
                returnMsg = "OCG收录卡包:"
                ResultMatch.forEach{
                    returnMsg+="\n收录时间:"+it.groupValues[1]+" 卡片编号:"+it.groupValues[2]+" 收录卡包:"+it.groupValues[3]
                }
            }
        }

        if(additionalInfo == "日文调整"){
            val WebData = GetWebSourceCode("https://ygocdb.com/card/"+cardNumber)
            val ResultMatch = Regex("""<div class="qa supplement"><ul><li>(.*?)</li></ul></div>""").find(WebData)?:return "没有调整"

            val replaceWith = Regex("""(<a .*?">)""")
            val resultString = replaceWith.replace(ResultMatch.groupValues[1], "")

            val replaceWith2 = Regex("""(</a>)""")
            val resultString2 = replaceWith2.replace(resultString, "")

            val effect = resultString2.split("</li><li>")
            returnMsg="卡片调整信息:\n"
            effect.forEach{
                returnMsg+=it
            }
            logger.info(returnMsg)
//            val ResultMatch = Regex()
        }

        if(additionalInfo == "日文faq"){
            val WebData = GetWebSourceCode("https://ygocdb.com/card/"+cardNumber)

            val QuestionResultMatch = Regex("""<hr>\s*<div class="qa question">(.*?)</div>""").findAll(WebData).toList()

            val AnswerResultMatch=Regex("""<div class="qa answer">(.*?)</div>""").findAll(WebData).toList()
            if(AnswerResultMatch.isNotEmpty()){
                returnMsg=""
                returnMsg += "日文F&Q"
                for(i in AnswerResultMatch.indices){
                    val replaceWith = Regex("""(<a .*?">)""")
                    val resultString = replaceWith.replace(QuestionResultMatch[i].groupValues[1], "")

                    val replaceWith2 = Regex("""(</a>)""")
                    val resultString2 = replaceWith2.replace(resultString, "")


                    val replaceWith1 = Regex("""(<a .*?">)""")
                    val resultString1 = replaceWith1.replace(AnswerResultMatch[i].groupValues[1], "")

                    val replaceWith3 = Regex("""(</a>)""")
                    val resultString3 = replaceWith3.replace(resultString1, "")

                    val replaceWith4 = Regex("""(<br>)""")
                    val resultString4 = replaceWith4.replace(resultString3, "")

                    returnMsg +="提问："+resultString2+"\n\n回答："+resultString4 + "{forwardmessage的分割符}"
                }
            }
        }

        return returnMsg
    }

    //查卡片价格
    fun SearchCardPrices(cardName: String):String{
        val cardToSearchName = URLEncoder.encode( cardName,"UTF-8")
        val WebData = GetWebSourceCode("https://api.jihuanshe.com/api/market/search/match-product?keyword=${cardToSearchName}&game_key=ygo&game_sub_key=ocg&page=1&type=card_version&token=") // 替换为你要请求的 URL

        val pageNumber = Regex(""""last_page":(.*?),"next_page_url":""").findAll(WebData).toList()[0].groupValues[1].toInt()


        val resultMatch = mutableListOf<MatchResult>()
        //获得所有的resultMatch
        repeat(pageNumber) {
            // 执行循环中的操作
            val webData = GetWebSourceCode("https://api.jihuanshe.com/api/market/search/match-product?keyword=${cardToSearchName}&game_key=ygo&game_sub_key=ocg&page=${it+1}&token=") // 替换为你要请求的 URL
            //先匹配出数量
            resultMatch.addAll(Regex(""""name_cn":"(.*?)","name_origin":"(.*?)"[\s\S]*?"card_id":(.*?),"number":"(.*?)","rarity":"(.*?)","image_url":"(.*?)","min_price":(.*?),"grade"""").findAll(webData).toList())

        }

        if(resultMatch.isEmpty()){
            return "没有找到相关的东西"
        }

        var returnMessage = ""

        resultMatch.forEach {
            //卡片名字
            var cardNameA =it.groupValues[1]
            val properties = Properties()
            properties.load(StringReader("unicodeString=$cardNameA"))

            cardNameA = properties.getProperty("unicodeString")


            //卡片名字
            var cardNameB = it.groupValues[2]

            val propertiesB = Properties()
            propertiesB.load(StringReader("unicodeString=$cardNameB"))

            cardNameB = propertiesB.getProperty("unicodeString")


            //所属卡包
            var cardPack = it.groupValues[4]
            val cardPackproperties = Properties()
            cardPackproperties.load(StringReader("unicodeString=$cardPack"))

            cardPack = cardPackproperties.getProperty("unicodeString")


            var rarity = it.groupValues[5]
            val rarityproperties = Properties()
            rarityproperties.load(StringReader("unicodeString=$rarity"))

            rarity = rarityproperties.getProperty("unicodeString")

            var lowestPrice = it.groupValues[7].replace("\"", "")
            //处理空价格
            if(lowestPrice == "null"){
                lowestPrice = "无成交价格"
            }

            val kardPicture = it.groupValues[6].replace("\\", "")
//            //非同名卡片（部分重叠）
//            if (cardNameA == cardName|| cardNameB == cardName){
//                //剔除RD卡片
//                if(cardPack.indexOf("RD\\/")> -1){
//                    returnMessage += "卡名：" + cardName + "    最低价格：" + it.groupValues[6] + "\n卡包：" + it.groupValues[3] + " 罕贵度：" + it.groupValues[4] + " Grade:" + it.groupValues[7] + "{forwardmessage的分割符}"
//                }
//            }
            if(cardPack.indexOf("RD/")==-1){
                if (cardNameA == cardName|| cardNameB == cardName){
                    returnMessage += "卡名:" + cardName + "    最低价格:" + lowestPrice + "\n卡片编号:" + cardPack + "    罕贵度:" + rarity +"{价格查询的图片}:"+kardPicture +"{forwardmessage的分割符}"
                }
            }
        }

        return returnMessage +"集换社价格"

//        // 卡名对不对,和是不是rd的卡
//        if(resultMatch){
//
//        }

    }

    //查卡列表
    fun SearchCard(cardToSearch:String,page:Int,cardNumber:Int,additionalInfo:String):String{
        //查卡
        val replaceWith = Regex(" ")
        val cardToSearchInURL =replaceWith.replace(URLEncoder.encode( cardToSearch,"UTF-8"),"+")



        val WebData = GetWebSourceCode("https://ygocdb.com/more?search=$cardToSearchInURL&start=$page")

        val cardIdresultMatch = Regex("""<h3><span>(\d*)</span>&nbsp;""").findAll(WebData).toList()

        val  resultMatch = Regex("""<h3><span>(\d*?)</span>&nbsp;[\s\S]*?<strong class="name"><span>(.*?)</span><br></strong>.*\s.*\s*(.*)""").findAll(WebData).toList()

        if(resultMatch.isEmpty()){
            return "没有找到相关的东西"
        }
        //输出文本
        var  outPutResult = ""
        var  resultNumber = 1

        //如果是查单卡或者只搜到了一张卡
        if(cardNumber!=0){
            //如果有特殊指令
            if(additionalInfo != ""){
                //屎山遗留，加个if来判断是不是查卡片价格
                if(additionalInfo == "查价格"){
                    return SearchCardPrices(resultMatch[cardNumber-1].groupValues[2])
                }
                return AdditionalCommandProcess(resultMatch[cardNumber-1].groupValues[1],additionalInfo)
            }
            return SearchCardInfo(resultMatch,cardNumber)
        }

        if(page==0&&resultMatch.size==1){
            return "只有一张卡"
        }

        //输出列表
        resultMatch.forEach {
            //只输出10张
            if(resultNumber == 11){
                return outPutResult
            }

            //处理匹配数据
            outPutResult += resultNumber.toString() + "："+it.groupValues[2]+"\n类型："

            val monsterMatch = Regex("""(.*?)<br>(.*)""").find(it.groupValues[3])
            outPutResult += if(monsterMatch == null){
                it.groupValues[3]
            } else{
                monsterMatch.groupValues[1]+"\n身板："+ monsterMatch.groupValues[2]
            }
            outPutResult += "\n卡片密码："+ cardIdresultMatch[resultNumber-1].groupValues[1]

            outPutResult += "{forwardmessage的图片}:"+cardIdresultMatch[resultNumber-1].groupValues[1]

            outPutResult += "{forwardmessage的分割符}"

            resultNumber++
        }
        return outPutResult + "卡片列表"
    }




    //处理指令
    @ConsoleExperimentalApi
    suspend fun ProcessingCommand(arg: String,user:Contact,GroupID:Long):String{

        val userID = user.id
        val isConnection: Boolean

        //MC相关
        //查分
        if(arg.startsWith("查成分 ")||arg.startsWith("查分 ")){
            val matchResult = Regex("""分 ([\s\S]*)""").find(arg)?:return "null"
            val playerToCheck = matchResult.groupValues[1]
            val playerNameInURL = URLEncoder.encode( playerToCheck,"UTF-8")

            //获取网页数据
            var WebData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/user?username=${playerNameInURL}")

            //val filePath = "a.txt"
            //File(filePath).appendText(WebData.toString())

            val  DPMatch = Regex("""pt":(\d*?),"entertain_win""").find(WebData)?:return "u1s1是不是查错人了，这没查到东西啊"
            val  DP= DPMatch.groupValues[1]

            val  RatioMatch = Regex(""""athletic_wl_ratio":"(.*?)","arena""").find(WebData)?:return "u1s1是不是查错人了，这没查到东西啊"
            val  Ratio= RatioMatch.groupValues[1]

            val  RankMatch = Regex(""""arena_rank":(.*?)}""").find(WebData)?:return "u1s1是不是查错人了，这没查到东西啊"
            val  Rank= RankMatch.groupValues[1]

            WebData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/firstwin?username=${playerNameInURL}")

            val  FWMatch = Regex(""""today":"(\d)"}""").find(WebData)?:return "u1s1是不是查错人了，这没查到东西啊"
            val  FW= FWMatch.groupValues[1]

            val tag = SearchTag(playerToCheck)

            return "玩家：$playerToCheck $tag \nD.P：$DP\n排名：$Rank\n胜率：$Ratio%\n今日首胜: $FW/1"
        }
        //查历史
        if(arg.startsWith("查历史 ")||arg.startsWith("查记录 ")){
            val matchResult = Regex(""" ([\s\S]*)""").find(arg)?:return "null"
            val playerToCheck = matchResult.groupValues[1]
            val playerNameInURL = URLEncoder.encode( playerToCheck,"UTF-8")

            //获取网页数据
            val WebData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/history?username=${playerNameInURL}&type=1&page_num=30")

            val  ResultMatch = Regex(""""usernamea":"(.*?)","usernameb":"(.*?)","[\s\S]*?"pta":(.*?),"ptb":(.*?),"pta_ex":(.*?),"ptb_ex":(.*?),"[\s\S]*?"start_time":"(.*?)T(.*?).000Z","end_time":"(.*?)T(.*?).000Z","winner":"(.*?)","isfirstwin":(.*?),""").findAll(WebData).toList()
            if(ResultMatch.isEmpty()){
                return "没这人的记录，要么没打过竞技要么没这人"
            }
            var  resultNumber = 1
            var outputResults = ""
            val format = DecimalFormat("#.##")
            //舍弃规则，RoundingMode.FLOOR表示直接舍弃。
            format.roundingMode = RoundingMode.CEILING
            ResultMatch.forEach{

                //时间加时区
                val resultTime = addEightHours(it.groupValues[7]+" "+it.groupValues[8],8)

                if(playerToCheck==it.groupValues[1]){
                    outputResults+= "${it.groupValues[1]} vs ${it.groupValues[2]}\n战况:"
                    if(it.groupValues[11]==playerToCheck){
                        outputResults+="胜利"+" D.P:"
                        outputResults += if(it.groupValues[12]=="true"){
                            "+"+format.format((it.groupValues[3].toFloat()-it.groupValues[5].toFloat()-4)).toString()+" +4(首胜)"
                        } else{
                            "+"+format.format((it.groupValues[3].toFloat()-it.groupValues[5].toFloat())).toString()
                        }
                    }
                    else{
                        outputResults+="败北 "+" D.P:"
                        outputResults+="-"+format.format(it.groupValues[5].toFloat()-it.groupValues[3].toFloat()).toString()
                    }

                    outputResults+="\n开始时间:"+resultTime +
                            " 耗时:"+CalculateTime(it.groupValues[7]+" "+it.groupValues[8],it.groupValues[9]+" "+it.groupValues[10])+"{forwardmessage的分割符}"
                }
                else{
                    outputResults+= "${it.groupValues[2]} vs ${it.groupValues[1]}\n战况:"
                    if(it.groupValues[11]==playerToCheck){
                        outputResults+="胜利"+" D.P:"
                        outputResults += if(it.groupValues[12]=="true"){
                            "+"+format.format((it.groupValues[4].toFloat()-it.groupValues[6].toFloat()-4)).toString()+" +4(首胜)"
                        } else{
                            "+"+format.format((it.groupValues[4].toFloat()-it.groupValues[6].toFloat())).toString()
                        }
                    }
                    else{
                        outputResults+="败北 "+" D.P:"
                        outputResults+="-"+format.format((it.groupValues[6].toFloat()-it.groupValues[4].toFloat())).toString()
                    }
                    //时间计算

                    outputResults+="\n开始时间:"+resultTime +
                            " 耗时:"+CalculateTime(it.groupValues[7]+" "+it.groupValues[8],it.groupValues[9]+" "+it.groupValues[10])+"{forwardmessage的分割符}"
                }

                resultNumber++
            }

            outputResults += playerToCheck+ "的MC对战历史"

            return outputResults
        }

        if(arg.contains("月历史 ")){
            val matchResult = Regex("""(\d*)月历史 ([\s\S]*)""").find(arg)?:return "null"
            val yearMatchResult = Regex("""(\d*)年(\d*)月历史 ([\s\S]*)""").find(arg)?:null
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)
            val currentMonth = calendar.get(Calendar.MONTH) + 1
            var targetYear = currentYear
            if(yearMatchResult!=null){
                targetYear = yearMatchResult.groupValues[1].toInt()
                if(targetYear<=2000){
                    targetYear = currentYear
                }
            }
            val TargetMonth = matchResult.groupValues[1].toInt()
            val dateFormat =  DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val targetDate:LocalDateTime
            if(TargetMonth <10){
                targetDate = LocalDateTime.parse("$targetYear-0$TargetMonth-01 00:00:00",dateFormat)
            }
            else{
                targetDate = LocalDateTime.parse("$targetYear-$TargetMonth-01 00:00:00",dateFormat)
            }
//            if(TargetMonth>currentMonth ||TargetMonth<0){
//                return "请正确输入月份"
//            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
//            val dateTime = LocalDateTime.parse(input, formatter)
//            val newDateTime = dateTime.plusHours(addTime)
//            }
            val playerToCheck = matchResult.groupValues[2]
            val playerNameInURL = URLEncoder.encode( playerToCheck,"UTF-8")

            //获取网页数据
            var WebData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/history?username=${playerNameInURL}&type=1&page_num=500")
            var  ResultMatch = Regex(""""usernamea":"(.*?)","usernameb":"(.*?)","[\s\S]*?"pta":(.*?),"ptb":(.*?),"pta_ex":(.*?),"ptb_ex":(.*?),"[\s\S]*?"start_time":"(.*?)T(.*?).000Z","end_time":"(.*?)T(.*?).000Z","winner":"(.*?)","isfirstwin":(.*?),""").findAll(WebData).toList()
            if(ResultMatch.isEmpty()){
                return "没这人的记录，要么没打过竞技要么没这人"
            }

            val format = DecimalFormat("#.##")
            //舍弃规则，RoundingMode.FLOOR表示直接舍弃。
            format.roundingMode = RoundingMode.CEILING


            //时间



            var FTime = LocalDateTime.parse(ResultMatch[ResultMatch.size-1].groupValues[7]+" "+ResultMatch[ResultMatch.size-1].groupValues[8], dateFormat)//搜索的结果里最早的时间
            FTime = FTime.plusHours(8)
            var pageNum = 500
            var StartNum = 0
            var RecordSize = 0
            var resultSize = 0
            logger.info(TargetMonth.toString())
            logger.info(FTime.toString())
            //查记录本身
            while (FTime >= targetDate){

                WebData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/history?username=${playerNameInURL}&type=1&page_num=" + pageNum)
                ResultMatch = Regex(""""usernamea":"(.*?)","usernameb":"(.*?)","[\s\S]*?"pta":(.*?),"ptb":(.*?),"pta_ex":(.*?),"ptb_ex":(.*?),"[\s\S]*?"start_time":"(.*?)T(.*?).000Z","end_time":"(.*?)T(.*?).000Z","winner":"(.*?)","isfirstwin":(.*?),""").findAll(WebData).toList()
                if(ResultMatch.size<pageNum-1){
                    break
                }
                FTime = LocalDateTime.parse(ResultMatch[ResultMatch.size-1].groupValues[7]+" "+ResultMatch[ResultMatch.size-1].groupValues[8], dateFormat)
                FTime = FTime.plusHours(8)
                logger.info(FTime.toString())
//                if(pageNum >= 10000){
//                    return "报错：超出页数极限"
//                }
                if(ResultMatch.size == resultSize){
                    return "超过最早记录"
                }
                resultSize = ResultMatch.size
                pageNum += pageNum*2
            }
            for(i in ResultMatch.size-1 downTo 0){
                var localTime = LocalDateTime.parse(ResultMatch[i].groupValues[7]+" "+ResultMatch[i].groupValues[8], dateFormat)
                localTime = localTime.plusHours(8)
                if(localTime.monthValue == TargetMonth && targetYear == localTime.year){
                    RecordSize = i
                    break
                }

            }

            logger.info(RecordSize.toString())

            if (RecordSize == 0 ){
                return "没有记录"
            }
            for(i in RecordSize downTo 0){
                var localTime = LocalDateTime.parse(ResultMatch[i].groupValues[7]+" "+ResultMatch[i].groupValues[8], dateFormat)
                localTime = localTime.plusHours(8)
                if(TargetMonth == 12){
                    if(localTime.monthValue == 1 && targetYear+1 == localTime.year){
                        StartNum = i+1
                        break
                    }
                }
                else{
                    if(localTime.monthValue > TargetMonth&& targetYear == localTime.year){
                        StartNum = i+1
                        break
                    }
                }
            }

            //胜率and打卡情况
            var FirstWinNum = 0
            var winNum =0
            for(i in StartNum..RecordSize){
                if(ResultMatch[i].groupValues[11]==playerToCheck){
                    winNum++
                    if(ResultMatch[i].groupValues[12]=="true"){
                        FirstWinNum++
                    }
                }
            }


            //分数变化
            val StartDP: Float = if(ResultMatch[RecordSize].groupValues[1] == playerToCheck){
                ResultMatch[RecordSize].groupValues[5].toFloat()
            } else{
                ResultMatch[RecordSize].groupValues[6].toFloat()
            }
            val EndDP: Float
            EndDP = if(ResultMatch[StartNum].groupValues[1] == playerToCheck){
                ResultMatch[StartNum].groupValues[3].toFloat()
            } else{
                ResultMatch[StartNum].groupValues[4].toFloat()
            }

            //排名
            var rankData =""
            if (TargetMonth <10){
                rankData =GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/historyScore?username=${playerNameInURL}&season=$targetYear-0${TargetMonth}")
            }
            else{
                rankData =GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/historyScore?username=${playerNameInURL}&season=$targetYear-${TargetMonth}")
            }
            logger.info("获得排名网址：https://sapi.moecube.com:444/ygopro/arena/historyScore?username=${playerNameInURL}&season=$targetYear-${TargetMonth}")
            val rankResultMatch = Regex("""rank":(.*?),"pt"""").find(rankData)?:null

            //String.format("%02d", date.monthValue)
            //logger.info("当前月份: $currentMonth" + "当前年份: $currentYear")
            if(rankResultMatch != null){
                return "玩家："+playerToCheck +"\n"+ TargetMonth+"月场次：" + (RecordSize+1-StartNum).toString() +"\n" + "打卡情况：" + FirstWinNum.toString() + "\n"+ TargetMonth+"月胜率：" +"%.2f".format((winNum.toFloat()/(RecordSize+1-StartNum).toFloat())*100)+"%"+"\n" + "月初分数：" + StartDP.toInt() + "||" + "月末分数：" + EndDP.toInt() + "\n"+ "分数变化：" + (EndDP - StartDP).toInt()+ "\n结算排名："+rankResultMatch.groupValues[1]
            }
            else{
                return "玩家："+playerToCheck +"\n"+ TargetMonth+"月场次：" + (RecordSize+1-StartNum).toString() +"\n" + "打卡情况：" + FirstWinNum.toString() + "\n"+ TargetMonth+"月胜率：" +"%.2f".format((winNum.toFloat()/(RecordSize+1-StartNum).toFloat())*100)+"%"+"\n" + "月初分数：" + StartDP.toInt() + "||" + "月末分数：" + EndDP.toInt() + "\n"+ "分数变化：" + (EndDP - StartDP).toInt()
            }
            //"||"+LTime.year.toString()+ "||" +LTime.monthValue.toString() + "||" + "当前月份: $currentMonth" + "当前年份: $currentYear")

        }

        if(arg.startsWith("对战列表")){
            val matchResult = Regex("""对战列表 (\d*)""").find(arg)?:return "格式不对 格式为对战列表 数字\n对战列表 1"

            val result = matchResult.groupValues[1]

            if(!IsNumber(result)){
                return "格式不对 可以试试发\n对战列表 1"
            }

            var page= result.toInt()

            QuickSort(duelList,0,duelList.size-1)

            //群聊 订阅用户优先显示
            if(GroupID.toInt() != -1){
                val subData =  GroupScribtion.data.getOrPut(GroupID){ GroupData(mutableListOf<String>())}

                for(i in 0..duelList.size-1){

                    var index = subData.SubscribedUser.indexOfFirst { it == duelList[i].Player1 }

                    if(index == -1){
                        index = subData.SubscribedUser.indexOfFirst { it == duelList[i].Player2 }
                    }

                    if(index != -1){
                        val temp = duelList[i]
                        duelList.removeAt(i)
                        duelList.add(0,temp)
                    }
                }
            }

            val current = LocalDateTime.now()

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            val formatted = current.format(formatter)

            var returnMsg = ""

            if(page <1){
                page = 1
            }
            if((page-1)*30>=duelList.size){
                return "超出最后一页"
            }

            var endNum = page*30-1

            if(endNum > duelList.size-1){
                endNum = duelList.size-1
            }

            var token = ""
            val subData =  PersonalSubscription.data.getOrPut(userID){ PersonalData(mutableListOf<String>())}
            if(subData.PlayerToken!="0"){
                token = subData.PlayerToken
            }



            for (i in ((page-1)*30)..endNum)
            {

                returnMsg +=WordsMatch( duelList[i].Player1)

                returnMsg += SearchTag(duelList[i].Player1)


                returnMsg += "\n排名："+duelList[i].player1Rank+"  胜率："+duelList[i].player1Ratio + "%"+ "\nVS\n"+
                        WordsMatch(duelList[i].Player2)

                returnMsg += SearchTag(duelList[i].Player2)
//                if(tagData[duelList[i].Player2] !=null ){
//                    if(tagData[duelList[i].Player2]?.isNotEmpty() == true){
//                        tagData[duelList[i].Player2]?.forEach{
//                            returnMsg += " #$it"
//                        }
//                    }
//                }

                returnMsg += "\n排名："+duelList[i].player2Rank+"  胜率："+duelList[i].player2Ratio+"%"

                returnMsg += if(duelList[i].StartTime!="-1"){
                    "\n对局已过去时间："+CalculateTime(duelList[i].StartTime,formatted)
                } else{
                    "\n对局已过去时间：未知"
                }

                //是否增加对战房间密码
                returnMsg += if(token != ""){
                    "\n对战房间密码:"+token+duelList[i].id  +"{forwardmessage的分割符}"
                } else{
                    "{forwardmessage的分割符}"
                }

            }
            returnMsg += "MC对战列表"
            return returnMsg
        }

        if(arg == "查萌卡排名"){
            val webData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/users?o=pt")
            val resultMatch = Regex(""""username":"(.*?)",[\s\S]*?"pt":(.*?),[\s\S]*?"athletic_win":(\d*),[\s\S]*?"athletic_lose":(\d*),[\s\S]*?"athletic_draw":(\d*),""").
                    findAll(webData).toList()

            if(resultMatch.isNotEmpty()){
                var returnMsg = ""
                var rank = 1

                resultMatch.forEach{
                    val tag = SearchTag(WordsMatch(it.groupValues[1]))
                    returnMsg +=rank.toString() +". 玩家名：" + WordsMatch(it.groupValues[1]) + tag + "\n胜率：" +
                            "%.2f".format((it.groupValues[3].toFloat()/(it.groupValues[3].toFloat()+it.groupValues[4].toFloat()+it.groupValues[5].toFloat()))*100) +
                            "% D.P："+it.groupValues[2].toFloat().toInt().toString() + "{forwardmessage的分割符}"
                    rank ++
                }

                returnMsg += "萌卡天梯排名"
                return returnMsg
            }
        }

        //连接MC服务器相关
        if(arg == "开始连接"){
            val index = Config.admin.indexOfFirst { it ==  userID}
            if (index == -1) return "你不是管理员，关于如何添加管理员。可以去GitHub上看readme"
            isConnection = true
            while (isConnection) {
                duelList.clear()
                LinkStart()
                Thread.sleep(3000)
                client.close()
                client = HttpClient(OkHttp) {
                    BrowserUserAgent()
                    install(WebSockets) {
                        pingInterval = 1000
                    }
                    install(HttpTimeout) {
                        socketTimeoutMillis = 1000
                    }
                }
                Thread.sleep(3000)
            }

            return "在连了"
        }

        if(arg == "关闭连接"){
            val index = Config.admin.indexOfFirst { it ==  userID}
            if (index == -1) return "你不是管理员，关于如何添加管理员。可以去GitHub上看readme"
            return "关了,离开了，不要爱了"
        }

        if(arg == "连接状态"){
            return if(client.isActive == true){
                "连着"
            } else{
                "断了"
            }
        }


        //查卡(百鸽)
        //开始查询
        if(arg.startsWith("查卡")||arg.startsWith("卡查")){
            //匹配查卡内容
            val matchResult = Regex("""[查|卡][查|卡] \s*(.*)""").find(arg)?:return "null"
            val cardToSearch = matchResult.groupValues[1]
            //更新用户数据
            val userSearchData = UpdateUserList(userID)
            userSearchData.UserSearchContent = cardToSearch
            userSearchData.UserSearchPage = 1
            userSearchData.UserSearchProcess =0
            UserSearchDataList.add(userSearchData)
//            val replaceWith = Regex("beautiful")
//            val resultString = replaceWith.replace("this picture is beautiful", "awesome")

            val returnMsg = SearchCard(cardToSearch,0,0,"")

            if(returnMsg == "只有一张卡"){
                val IsInUserList = UpdateUserList(userID)//获得用户数据

                //修改用户数据并添加到列表
                IsInUserList.UserSearchCard = 1
                IsInUserList.UserSearchProcess = 1
                UserSearchDataList.add(IsInUserList)

                return SearchCard(cardToSearch,0,1,"")
            }

            return returnMsg
        }

        //翻页
        if(arg == "上一页"){
            val IsInUserList = UpdateUserList(userID)//获得用户数据

            //不存在则返回
            if(IsInUserList.UserSearchContent == ""){
                return "null"
            }
            else if(IsInUserList.UserSearchProcess.toInt() == 1)//如果是在单卡查询里了
            {
                if(IsInUserList.UserSearchPage>1){
                    //修改用户数据并添加到列表
                    IsInUserList.UserSearchCard -= 1
                    IsInUserList.UserSearchProcess = 1
                    UserSearchDataList.add(IsInUserList)
                    //返回单卡数据
                    return SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"")
                }
            }
            else//翻页
            {
                var Page = IsInUserList.UserSearchPage
                return if(Page>1){
                    Page -= 1
                    IsInUserList.UserSearchPage -=1
                    UserSearchDataList.add(IsInUserList)

                    SearchCard(IsInUserList.UserSearchContent,(Page-1)*10,0,"")
                } else{
                    UserSearchDataList.add(IsInUserList)
                    "已经是第一页了"
                }
            }
        }

        //下一页
        if(arg == "下一页"){
            val IsInUserList = UpdateUserList(userID)//获得用户数据

            //不存在则返回
            if(IsInUserList.UserSearchContent == ""){
                return "null"
            }
            else if(IsInUserList.UserSearchProcess.toInt() == 1)//如果是在单卡查询里了
            {
                if(IsInUserList.UserSearchPage<10){
                    //修改用户数据并添加到列表
                    IsInUserList.UserSearchCard += 1
                    IsInUserList.UserSearchProcess = 1
                    UserSearchDataList.add(IsInUserList)
                    //返回单卡数据
                    return SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"")
                }
            }
            else//翻页
            {
                val Page = IsInUserList.UserSearchPage
                val content = SearchCard(IsInUserList.UserSearchContent,Page*10,0,"")

                //是否有内容，没有则是最后一页
                return if(content == "没有找到相关的东西") {
                    UserSearchDataList.add(IsInUserList)

                    "已经是最后一页了"
                } else{
                    IsInUserList.UserSearchPage+=1
                    UserSearchDataList.add(IsInUserList)

                    content
                }

//                if(Page>1){
//                    Page -= 1
//                    IsInUserList.UserSearchPage -=1
//                    UserSearchDataList.add(IsInUserList)
//                    return SearchCard(IsInUserList.UserSearchContent,(Page-1)*10)
//                }
//                else{
//                    UserSearchDataList.add(IsInUserList)
//                    return "已经是第一页了"
//                }
            }
        }

        //进入单卡
        if(arg.startsWith("进入单卡 ")){
            val matchResult = Regex("""进入单卡 (\d*)""").find(arg)?:return "null"
            val cardNumber = matchResult.groupValues[1]

            if(cardNumber.toInt()<11){
                val IsInUserList = UpdateUserList(userID)//获得用户数据

                //不存在则返回
                if(IsInUserList.UserSearchContent == ""){
                    return "null"
                }
                else if(IsInUserList.UserSearchProcess.toInt() == 1)//如果是在单卡查询里了
                {
                    //修改用户数据并添加到列表
                    IsInUserList.UserSearchCard = cardNumber.toInt()
                    IsInUserList.UserSearchProcess = 1
                    UserSearchDataList.add(IsInUserList)
                    //返回单卡数据
                    return SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"")
                }
                else//翻页//翻页
                {
                    //修改用户数据并添加到列表
                    IsInUserList.UserSearchCard = cardNumber.toInt()
                    IsInUserList.UserSearchProcess = 1
                    UserSearchDataList.add(IsInUserList)
                    //返回单卡数据
                    return SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"")
                }
            }
        }

        //退出单卡
        if(arg == "返回"||arg == "退出"){
            val IsInUserList = UpdateUserList(userID)//获得用户数据

            //不存在则返回
            if(IsInUserList.UserSearchContent == ""){
                return "null"
            }
            else if(IsInUserList.UserSearchProcess.toInt() == 1)//如果是在单卡查询里了
            {
                //修改用户数据并添加到列表
                IsInUserList.UserSearchCard = 0
                IsInUserList.UserSearchProcess = 0
                UserSearchDataList.add(IsInUserList)
                //返回单卡数据
                return SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,0,"")
            }
        }

        if(arg == "卡片收录"){
            val IsInUserList = UpdateUserList(userID)//获得用户数据

            //不存在则返回
            if(IsInUserList.UserSearchContent == ""){
                return "null"
            }
            else if(IsInUserList.UserSearchProcess.toInt() == 1)//如果是在单卡查询里了
            {
                //修改用户数据并添加到列表
                UserSearchDataList.add(IsInUserList)
                //返回单卡数据
                return SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"md卡包") + "\n" +
                        SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"ocg收录")
            }
        }

        if(arg.startsWith("卡片收录 ")){
            val matchResult = Regex("""卡片收录 (\d*)""").find(arg)?:return "null"
            val cardNumber = matchResult.groupValues[1]

            if(cardNumber.toInt()<11){
                val IsInUserList = UpdateUserList(userID)//获得用户数据

                //不存在则返回
                if(IsInUserList.UserSearchContent == ""){
                    return "null"
                }
                else if(IsInUserList.UserSearchProcess.toInt() == 1)//如果是在单卡查询里了
                {
                    //修改用户数据并添加到列表
                    IsInUserList.UserSearchCard = cardNumber.toInt()
                    IsInUserList.UserSearchProcess = 0
                    UserSearchDataList.add(IsInUserList)
                    //返回单卡数据
                    return SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"md卡包") + "\n" +
                            SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"ocg收录")
                }
                else//翻页//翻页
                {
                    //修改用户数据并添加到列表
                    IsInUserList.UserSearchCard = cardNumber.toInt()
                    IsInUserList.UserSearchProcess = 0
                    UserSearchDataList.add(IsInUserList)
                    //返回单卡数据
                    return SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"md卡包") + "\n" +
                            SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"ocg收录")
                }

            }
        }

        if(arg == "卡片调整"){
            val IsInUserList = UpdateUserList(userID)//获得用户数据

            //不存在则返回
            if(IsInUserList.UserSearchContent == ""){
                return "null"
            }
            else
            {
                //修改用户数据并添加到列表
                UserSearchDataList.add(IsInUserList)
                var returnMessage = SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"日文调整") +
                        "{forwardmessage的分割符}" +
                        SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"日文faq")
                if(returnMessage.length>5000){
                    //查卡
                    val replaceWith = Regex(" ")
                    val cardToSearchInURL =replaceWith.replace(URLEncoder.encode(IsInUserList.UserSearchContent,"UTF-8"),"+")



                    val WebData = GetWebSourceCode("https://ygocdb.com/more?search=$cardToSearchInURL&start=${(IsInUserList.UserSearchPage-1)*10}")

                    val  resultMatch = Regex("""<h3><span>(\d*?)</span>&nbsp;[\s\S]*?<strong class="name"><span>(.*?)</span><br></strong>.*\s.*\s*(.*)""").findAll(WebData).toList()

                    if(resultMatch.isEmpty()){
                        return "没有找到相关的东西"
                    }

                    val WebDataCard = GetWebSourceCode("https://ygocdb.com/card/"+resultMatch[IsInUserList.UserSearchCard - 1].groupValues[1])
                    val QAUrl= Regex("""title="数据库编号">(\d*)</span>""").findAll(WebDataCard).toList()

                    returnMessage = "长度太长，超出QQ发送字数限制了，自己去官网看吧\n网址：https://www.db.yugioh-card.com/yugiohdb/faq_search.action?ope=4&cid=${QAUrl[0].groupValues[1]}&request_locale=ja"

                }
                //返回单卡数据
                return returnMessage
            }
        }

        if(arg.startsWith("卡片调整 ")){
            val matchResult = Regex("""卡片调整 (\d*)""").find(arg)?:return "null"
            val cardNumber = matchResult.groupValues[1]

            if(cardNumber.toInt()<11){
                val IsInUserList = UpdateUserList(userID)//获得用户数据

                //不存在则返回
                if(IsInUserList.UserSearchContent == ""){
                    return "null"
                }
                else//翻页//翻页
                {
                    //修改用户数据并添加到列表
                    IsInUserList.UserSearchCard = cardNumber.toInt()
                    IsInUserList.UserSearchProcess = 0
                    UserSearchDataList.add(IsInUserList)


                    var returnMessage = SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"日文调整") +
                            "{forwardmessage的分割符}" +
                            SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"日文faq")
                    if(returnMessage.length>5000) {
                        //查卡
                        val replaceWith = Regex(" ")
                        val cardToSearchInURL =
                            replaceWith.replace(URLEncoder.encode(IsInUserList.UserSearchContent, "UTF-8"), "+")


                        val WebData =GetWebSourceCode("https://ygocdb.com/more?search=$cardToSearchInURL&start=${(IsInUserList.UserSearchPage - 1) * 10}")

                        val resultMatch =
                            Regex("""<h3><span>(\d*?)</span>&nbsp;[\s\S]*?<strong class="name"><span>(.*?)</span><br></strong>.*\s.*\s*(.*)""").findAll(WebData).toList()

                        if (resultMatch.isEmpty()) {
                            return "没有找到相关的东西"
                        }
                        val WebDataCard = GetWebSourceCode("https://ygocdb.com/card/"+resultMatch[IsInUserList.UserSearchCard - 1].groupValues[1])
                        val QAUrl= Regex("""title="数据库编号">(\d*)</span>""").findAll(WebDataCard).toList()

                        returnMessage = "长度太长，超出QQ发送字数限制了，自己去官网看吧\n网址：https://www.db.yugioh-card.com/yugiohdb/faq_search.action?ope=4&cid=${QAUrl[0].groupValues[1]}&request_locale=ja"

                    }

                    //返回单卡数据
                    return returnMessage
                }

            }
        }

        if(arg == "卡片价格"){
            val IsInUserList = UpdateUserList(userID)//获得用户数据

            //不存在则返回
            return if(IsInUserList.UserSearchContent == ""){
                "null"
            } else {
                //修改用户数据并添加到列表
                UserSearchDataList.add(IsInUserList)
                //返回单卡数据
                SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"查价格")
            }
        }

        if(arg.startsWith("卡片价格 ")){
            val matchResult = Regex("""卡片价格 (\d*)""").find(arg)?:return "null"
            val cardNumber = matchResult.groupValues[1]

            if(cardNumber.toInt()<11){
                val IsInUserList = UpdateUserList(userID)//获得用户数据

                //不存在则返回
                if(IsInUserList.UserSearchContent == ""){
                    return "null"
                }
                else if(IsInUserList.UserSearchProcess.toInt() == 1)//如果是在单卡查询里了
                {
                    //修改用户数据并添加到列表
                    IsInUserList.UserSearchCard = cardNumber.toInt()
                    IsInUserList.UserSearchProcess = 0
                    UserSearchDataList.add(IsInUserList)
                    //返回单卡数据
                    return SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"查价格")
                }
                else//翻页//翻页
                {
                    //修改用户数据并添加到列表
                    IsInUserList.UserSearchCard = cardNumber.toInt()
                    IsInUserList.UserSearchProcess = 0
                    UserSearchDataList.add(IsInUserList)
                    //返回单卡数据
                    return SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"查价格")
                }

            }
        }


        //机器人相关指令
        if(arg == "释放卡查内存"){
            val index = Config.admin.indexOfFirst { it ==  userID}
            if (index == -1) return "你不是管理员，关于如何添加管理员。可以去GitHub上看readme"
            UserSearchDataList.clear()
            return "释放自我！"
        }

        if(arg.startsWith("添加群订阅 ")){


            val matchResult = Regex("""添加群订阅 (.*)""").find(arg)?:return "添加失败，格式为\n添加群订阅 用户名"
            val groupID = GroupID


            if(groupID.toInt() == -1){
                return "添加失败，请在群里使用这个指令"
            }

            val playerName = matchResult.groupValues[1]

            val subData =  GroupScribtion.data.getOrPut(groupID){ GroupData(mutableListOf<String>())}

            return subData.AddSubscribeUser(playerName)

        }

        if(arg.startsWith("删除群订阅 ")){

            val matchResult = Regex("""删除群订阅 (.*)""").find(arg)?:return "删除失败，格式为\n删除群订阅 用户名"
            val groupID = GroupID


            if(groupID.toInt() == -1){
                return "添加失败，请在群里使用这个指令"
            }
            val playerName = matchResult.groupValues[1]

            val subData =  GroupScribtion.data.getOrPut(groupID){ GroupData(mutableListOf<String>())}

            return subData.DissubscribeUser(playerName)

        }

        if(arg == "显示群订阅"){

            val groupID = GroupID
            if(groupID.toInt() == -1){
                return "请在群里使用这个指令"
            }

            val subData =  GroupScribtion.data.getOrPut(groupID){ GroupData(mutableListOf<String>())}

            var returnMsg = "该群订阅玩家:"

            subData.SubscribedUser.forEach{
                returnMsg +="\n" + it
            }
            return returnMsg
        }

        if(arg.startsWith("添加个人订阅 ")){


            val matchResult = Regex("""添加个人订阅 (.*)""").find(arg)?:return "添加失败，格式为\n添加个人订阅 用户名"


            val playerName = matchResult.groupValues[1]

            val subData =  PersonalSubscription.data.getOrPut(userID){ PersonalData(mutableListOf<String>())}

            return subData.AddSubscribeUser(playerName)

        }

        if(arg.startsWith("删除个人订阅 ")){

            val matchResult = Regex("""删除个人订阅 (.*)""").find(arg)?:return "删除失败，格式为\n删除个人订阅 用户名"

            val playerName = matchResult.groupValues[1]

            val subData =  PersonalSubscription.data.getOrPut(userID){ PersonalData(mutableListOf<String>())}

            return subData.DissubscribeUser(playerName)

        }

        if(arg == "显示个人订阅"){


            val subData =  PersonalSubscription.data.getOrPut(userID){ PersonalData(mutableListOf<String>())}

            var returnMsg = ""

            subData.SubscribedUser.forEach{
                val playerNameInURL = URLEncoder.encode( it,"UTF-8")
                val webData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/user?username=${playerNameInURL}")

                val  PlayerinfoMatch1 = Regex(""","pt":(\d*),".*?"athletic_wl_ratio":"(.*?)","arena_rank":(.*?)}""").find(webData)

                if(PlayerinfoMatch1!=null){
                    returnMsg += "玩家："+it+"\nD.P:"+PlayerinfoMatch1.groupValues[1]+"\n排名："+
                            PlayerinfoMatch1.groupValues[3]+"\n胜率:"+PlayerinfoMatch1.groupValues[2]+"{forwardmessage的分割符}"
                }
            }
            return returnMsg
        }

        if(arg.startsWith("登录到萌卡 ")){
            val matchResult = Regex("""登录到萌卡 (\S*) (\S*)""").find(arg)?:return "格式失败 格式为登录到萌卡 账号 密码"

            val account = matchResult.groupValues[1]

            val passWord = matchResult.groupValues[2]

            val accountInfo = login(account,passWord)

            if(accountInfo != ""){
                val accountIDMatch = Regex("""id":(\d*),"username":"(.*?)"""").find(accountInfo)?:return accountInfo
                val token=generateToken(accountIDMatch.groupValues[1].toInt())

                val subData =  PersonalSubscription.data.getOrPut(userID){ PersonalData(mutableListOf<String>())}

                subData.PlayerToken = token
                subData.PlayerName = accountIDMatch.groupValues[2]

                return "账号名：" + accountIDMatch.groupValues[2] + "登录成功。\n你的观战和房间token为："+token+"\n如果忘了可以发 萌卡账号token 来随时查看"+
                         "萌卡的ygo服务器为：tiramisu.mycard.moe\n竞技匹配端口为：8911\n观战房间密码为：你的token+对战房间ID"
            }

            return "登录失败，请检查你的账号密码"
        }

        if(arg == "萌卡账号token"){
            val subData =  PersonalSubscription.data.getOrPut(userID){ PersonalData(mutableListOf<String>())}

            if(subData.PlayerToken!="0"){
                return "你当前绑定账号用户名为:"+subData.PlayerName+"\n你的观战和房间token为:"+subData.PlayerToken +
                            "萌卡的ygo服务器为：tiramisu.mycard.moe\n竞技匹配端口为：8911\n观战房间密码为：你的token+对战房间ID"
            }

            return "你还没登录，先去登录吧"
        }

        if(arg == "饼图"){

            val WebData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/analytics/deck/type?type=day&source=mycard-athletic")

            val resultMatch = Regex(""""name":"(.*?)","recent_time":".*?count":"(.*?)",".*?,"win":"(.*?)","draw":".*?","lose":"(.*?)"},.*?"win":"(.*?)","draw":".*?","lose":"(.*?)"""").
            findAll(WebData).toList()


            val dataset = DefaultPieDataset<String>()

//            var totalNum = 0
//
//            for(i in 0..24){
//                totalNum += resultMatch[i].groupValues[2].toInt()
//            }

            if(resultMatch.isNotEmpty()){
                for(i in 0..24){
                    dataset.setValue(resultMatch[i].groupValues[1], resultMatch[i].groupValues[2].toInt())
                }
            }

            // 创建数据集

            // 创建数据集


            // 创建JFreeChart对象


            // 创建JFreeChart对象
            val chart = ChartFactory.createPieChart(
                "萌卡前25卡组使用率饼图",  // 图表标题
                dataset,  // 数据集
                true,  // 是否显示图例
                true,  // 是否生成工具提示
                false // 是否生成URL链接
            )

            val plot = chart.plot as PiePlot<*>
            val font = Font("宋体", Font.BOLD, 18)
            plot.labelFont = Font("宋体", Font.BOLD, 14)
            chart.title.font = Font("宋体", Font.BOLD, 24)

            plot.labelBackgroundPaint = null

            val labelGenerator = StandardPieSectionLabelGenerator("{0} ({2})", Locale.CHINA)
            plot.labelGenerator = labelGenerator
            plot.labelDistributor = PieLabelDistributor(1)

            // Set the margin to zero so the chart fills the whole panel
            plot.interiorGap = 0.0


            val legend = chart.legend
            legend.itemFont = font


            val labelGeneratorr = object : StandardPieSectionLabelGenerator() {
                override fun generateSectionLabel(dataset: PieDataset<*>?, key: Comparable<*>?): String {
                    var label = super.generateSectionLabel(dataset, key)
                    for(i in 0..24){
                        if(label == resultMatch[i].groupValues[1]){
                            val rate = ((resultMatch[i].groupValues[3].toFloat() + resultMatch[i].groupValues[6].toFloat())/
                                    (resultMatch[i].groupValues[3].toFloat() + resultMatch[i].groupValues[5].toFloat() +
                                        resultMatch[i].groupValues[4].toFloat() +resultMatch[i].groupValues[6].toFloat()))*100
                            label += " 使用数：" + resultMatch[i].groupValues[2] + " 综合胜率：" + "%.2f".format(rate) +"%"
                        }
                    }
                    return label
                }
            }

            plot.legendLabelGenerator = labelGeneratorr

            legend.position = RectangleEdge.RIGHT
            legend.setMargin(0.0, 0.0, 0.0, 10.0)
            legend.frame = BlockBorder.NONE
            legend.itemLabelPadding = RectangleInsets(2.0, 5.0, 2.0, 5.0)
            legend.legendItemGraphicPadding = RectangleInsets(0.0, 10.0, 0.0, 10.0)
            legend.setHorizontalAlignment(HorizontalAlignment.LEFT)


            chart.fireChartChanged()
            // 输出为PNG图片

            // 输出为PNG图片
            val outputFile: FileOutputStream?
            try {
                outputFile = FileOutputStream("./data/YGOAssistant/piechart.png")
                ChartUtils.writeChartAsPNG(outputFile, chart, 1080, 720)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return "{饼图}"
        }

        if(arg.startsWith("添加tag ")||arg.startsWith("扣帽子 ")){
            val matchResult = Regex(""".*? ([\s\S]*) ([\s\S]*)""").find(arg)?:return "null"
            val playerToCheck = matchResult.groupValues[1]
            val playerNameInURL = URLEncoder.encode( playerToCheck,"UTF-8")

            //获取网页数据
            val WebData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/user?username=${playerNameInURL}")

            //val filePath = "a.txt"
            //File(filePath).appendText(WebData.toString())

            Regex("""pt":(\d*?),"entertain_win""").find(WebData)?:return "没这人"



            val subData =  GroupPlayerTags.data.getOrPut(playerToCheck){ RobotTagData(mutableListOf<String>()) }

            if(subData.RobotTagData.indexOf(matchResult.groupValues[2])!=-1){
                return "该tag已经添加过了"
            }

            GroupPlayerTags.data[playerToCheck]?.RobotTagData?.add(matchResult.groupValues[2])

            var returnMessage = "添加成功，${playerToCheck}的tag："

            subData.RobotTagData.forEach{
                returnMessage+="#"+it+" "
            }

            return returnMessage
        }


        if(arg.startsWith("查看tag")||arg.startsWith("看看#")){
            var returnMessage ="以下为本机器人下所有玩家及tag"
            GroupPlayerTags.data.forEach{
                val subsubData = it.value

                if(subsubData.RobotTagData.isEmpty()){
                    //GroupPlayerTags.data.remove(it.key)
                }
                else{
                    returnMessage += "\n"
                    returnMessage += it.key +":"
                    subsubData.RobotTagData.forEach{
                        returnMessage +="#"+it+" "
                    }
                }

            }
            return returnMessage
        }

        if(arg.startsWith("删除tag ")||arg.startsWith("掉帽子 ")){
            val matchResult = Regex(""".*? ([\s\S]*) ([\s\S]*)""").find(arg)?:return "null"
            val subData =  GroupPlayerTags.data

            if(subData.get(matchResult.groupValues[2])?.RobotTagData?.isNotEmpty() == true){
                return "玩家并无该tag"
            }

            GroupPlayerTags.data.remove(matchResult.groupValues[2])

            var returnMessage = "删除成功！当前玩家tag："

            subData.forEach{
                returnMessage+="#"+it+" "
            }

            return returnMessage
        }
        return "null"
    }


}


class UserSearchData{
    var UesrQQID:Long = 0
    var UserSearchContent:String = ""
    var UserSearchProcess:Long = 0 //当前查到那了 0：开始查了，显示所有匹配内容列表。 1：进入了特定单卡
    var UserSearchPage = 1
    var UserSearchCard = 0
}

class DuelInfo{
    var id=""
    var Player1= ""
    var player1Ratio = "0"
    var player1Rank = "2147483647"
    var Player2= ""
    var player2Ratio = "0"
    var player2Rank = "2147483647"
    var StartTime="-1"

    fun GetPlayerInfo(){
        Thread {
            val playerNameInURL1 = URLEncoder.encode( Player1,"UTF-8")
            val WebData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/user?username=${playerNameInURL1}")

            val  PlayerinfoMatch1 = Regex(""""athletic_wl_ratio":"(.*?)","arena_rank":(.*?)}""").find(WebData)
            if(PlayerinfoMatch1!=null){
                if(PlayerinfoMatch1.groupValues[1] == "0"){
                    player1Ratio = "不存在"
                    player1Rank= "2147483647"
                }
                else{
                    player1Ratio = PlayerinfoMatch1.groupValues[1]
                    player1Rank= PlayerinfoMatch1.groupValues[2]
                }
            }

            val playerNameInURL2 = URLEncoder.encode( Player2,"UTF-8")
            val WebData2 = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/user?username=${playerNameInURL2}")

            val  PlayerinfoMatch2 = Regex(""""athletic_wl_ratio":"(.*?)","arena_rank":(.*?)}""").find(WebData2)
            if(PlayerinfoMatch2!=null){
                if(PlayerinfoMatch2.groupValues[1] == "0"){
                    player2Ratio = "不存在"
                    player2Rank= "2147483647"
                }
                else{
                    player2Ratio = PlayerinfoMatch2.groupValues[1]
                    player2Rank= PlayerinfoMatch2.groupValues[2]
                }
            }
        }.start()
    }

    //获取网页数据函数
    fun GetWebSourceCode(url:String):String{

        val doc = URL(url).readText()
        return doc
    }
}