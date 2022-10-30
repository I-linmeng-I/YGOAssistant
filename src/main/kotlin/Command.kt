package Linmeng


import Linmeng.YGOAssisttant.logger
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.Json
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.util.ContactUtils.getContactOrNull
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.AtAll
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import java.lang.Double.parseDouble
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import net.mamoe.mirai.utils.info
import java.io.File
import java.io.FileInputStream
import java.net.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

class LoginCredentialException : HttpStatusCodeException(HttpStatusCode.BadRequest)

open class HttpStatusCodeException(val code: HttpStatusCode) : RuntimeException()

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

    suspend fun login(username: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val ret = loginclient.submitForm<String>(
                    "https://api.moecube.com/accounts/" + "signin",
                    Parameters.build {
                        append("account", username)
                        append("password", password)
                    }, false
                )
                when {
                    ret != "" -> return@runCatching ret
                    else -> throw LoginCredentialException()

                }
            }
        }

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
        if(num1>num2){
            return num2
        }
        else{
            return  num1
        }
    }

    fun partition(li:MutableList<DuelInfo>, left:Int, right:Int):Int{
        var leftin = left
        var rightin = right
        var tmp = GetSmaller(li[leftin].player1Rank.toInt(),li[leftin].player2Rank.toInt())
        var tmpDuelInfo = li[leftin]
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
        var leftin = left
        var rightin = right
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

    //计算时间
    fun CalculateTime(startTime:String,endTime:String):String{
        var dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

        var sTime: Date = dateFormat.parse(startTime)
        var eTime: Date = dateFormat.parse(endTime)
        println("eTime=$eTime")
        val diff = eTime.time - sTime.time
        val days = diff / (1000 * 60 * 60 * 24)
        val hours = (diff - days * (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
        val minutes = ((diff - days * (1000 * 3600 * 24)) - hours * (1000 * 3600)) / (1000 * 60)
        val second = (diff - days * 1000 * 3600 * 24 - hours * 1000 * 3600 - minutes * 1000 * 60) / 1000
        if(hours.toInt()==0){
            return "$minutes:$second"
        }
        else{
            return "$hours:$minutes:$second"
        }
    }
    //是不是数字
    fun IsNumber(args: String):Boolean {

        val string = args
        var numeric = true

        try {
            val num = parseDouble(string)
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
        var userSearchData = UserSearchData()
        userSearchData.UesrQQID = userID
        userSearchData.UserSearchContent =""
        userSearchData.UserSearchProcess = 0
        userSearchData.UserSearchPage = 1

        return userSearchData
    }


    //连接MC和接收处理数据
    private var client = HttpClient(OkHttp) {
        install(WebSockets) {
            pingInterval = 10_000
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 10_000
        }
    }

    suspend fun LinkStart() {
        YGOAssisttant.logger.info { "开始尝试连接" }
        var ret = runCatching {client.wss(host = "tiramisu.mycard.moe", port = 8923, path = "?filter=started") {
                //                    // cancel if long time no init event
                //                    handler.sendEmptyMessageDelayed(
                //                        MSG_CANCEL_MONITOR_JOB, INTERVAL_LONG_TIME_NO_INIT
                //                    )
                while (isActive) (incoming.receive() as? Frame.Text)?.parse()
            }
        }
        if (ret.exceptionOrNull()!=null){
            restart()
        }
    }


    private suspend fun restart(){
        duelList.clear()
        client.close()
        client.cancel()
        client = HttpClient(OkHttp) {
            install(WebSockets) {
                pingInterval = 10_000
            }
            install(HttpTimeout) {
                socketTimeoutMillis = 10_000
            }
        }
        LinkStart()
        delay(5000)
        if(duelList.isEmpty()){
            restart()
        }
    }

    private suspend fun Frame.Text.parse() {


        val root = readText()

        val eventRegex = Regex(""""event":"(.*?)","""").find(root)

        if(eventRegex!=null){

            //            if (client.isActive) client.cancel()
            YGOAssisttant.logger.info { "接收到MC对局数据 tpye="+ eventRegex.groupValues[1]}
            when (eventRegex.groupValues[1]) {
                "init" -> {

                    val ResultMatch = Regex(""""id":"(.*?)",".*?"username":"(.*?)",".*?"username":"(.*?)","pos""").findAll(root).toList()


                    if(ResultMatch.isNotEmpty()){
                        ResultMatch.forEach{
                            var duelInfo = DuelInfo()
                            duelInfo.id = it.groupValues[1]
                            duelInfo.Player1 = it.groupValues[2]
                            duelInfo.Player2 = it.groupValues[3]

                            duelInfo.GetPlayerInfo()

                            duelList.add(duelInfo)
                        }
                    }

                    YGOAssisttant.logger.info {"初始化对局列表完成"}
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

                        var duelInfo = DuelInfo()
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
                                    Bot.instances.forEach {bot ->

                                        val subject = bot.getContactOrNull(userid)
                                        if(subject!=null){
                                            var returnMsg="你订阅的玩家：" + WordsMatch(player1) + " \n开始了和\n" + WordsMatch(player2) + " 的对战"
                                            if(playerToken!="0"&& player1 != playerName){
                                                returnMsg += "\n观战服务器为：tiramisu.mycard.moe\n端口为：8911\n观战房间密码为：$playerToken$duelID"
                                            }
                                            subject.sendMessage(returnMsg)
                                        }

                                        if(player1=="我是超级抽卡王"){

                                            val subject1 = bot.getGroup(923717437)

                                            if(subject1!=null){
                                                val message = buildMessageChain {
                                                    +Image(FileInputStream("./data/YGOAssistant/我是超级抽卡王.jpg").uploadAsImage(subject1).imageId)
                                                    //+AtAll
                                                }
                                                subject1.sendMessage(message)
                                            }

                                        }
                                    }
                                    return@launch
                                }

                                if(index != -1){
                                    Bot.instances.forEach {bot ->


                                        val subject = bot.getContactOrNull(userid)
                                        if(subject!=null&& player2 != playerName){
                                            var returnMsg="你订阅的玩家：" + WordsMatch(player2) + " \n开始了和\n" + WordsMatch(player1) + " 的对战"
                                            if(playerToken!="0"){
                                                returnMsg += "\n观战服务器为：tiramisu.mycard.moe\n端口为：8911\n观战房间密码为：$playerToken$duelID"
                                            }
                                            subject.sendMessage(returnMsg)
                                        }

                                        if(player2=="我是超级抽卡王"){
                                            val subject1 = bot.getGroup(923717437)

                                            if(subject1!=null){
                                                val message = buildMessageChain {
                                                    +Image(FileInputStream("./data/YGOAssistant/我是超级抽卡王.jpg").uploadAsImage(subject1).imageId)
                                                    //+AtAll
                                                }
                                                subject1.sendMessage(message)
                                            }
                                        }
                                    }
                                }
                            }
                        }


                        duelList.add(duelInfo)

                    }
                }
                else ->{
                    YGOAssisttant.logger.info {root}
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

        var avail = Regex("""<i class="(.*?)">""").find(CardData2).toString()
        var availMatch = ""
        if(avail == "l0"){
            availMatch = "禁止卡"
        }
        else if(avail == "l1"){
            availMatch = "限制卡"
        }
        else if(avail == "l2"){
            availMatch = "准限制卡"
        }
        else{
            availMatch = "无限制卡"
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
        if(monsterMatch == null){
            outPutResult+=ResultMatch[cardNumber-1].groupValues[3]+"\n"
        }
        else{
            outPutResult +=monsterMatch.groupValues[1]+"\n身板："+ monsterMatch.groupValues[2]+"\n"
        }
        outPutResult+="卡片ID："+ResultMatch[cardNumber-1].groupValues[1]

        if(availMatch !=null){
            outPutResult+= "\n禁限情况："+availMatch + "{分割多段}"
        }

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

        return outPutResult+"{加入图片}：url：${ResultMatch[cardNumber-1].groupValues[1]}"
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
            returnMsg="卡片调整信息:"
            effect.forEach{
                returnMsg+="\n"+it
            }

//            val ResultMatch = Regex()
        }

        if(additionalInfo == "日文faq"){
            val WebData = GetWebSourceCode("https://ygocdb.com/card/"+cardNumber)

            val QuestionResultMatch = Regex("""<hr>\s*<div class="qa question">(.*?)</div>""").findAll(WebData).toList()

            val AnswerResultMatch=Regex("""<div class="qa answer">(.*?)</div>""").findAll(WebData).toList()
            if(AnswerResultMatch.isNotEmpty()){
                returnMsg=""
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

                returnMsg += "日文F&Q"
            }
        }

        return returnMsg
    }

    //查卡列表
    fun SearchCard(cardToSearch:String,page:Int,cardNumber:Int,additionalInfo:String):String{
        //查卡
        val replaceWith = Regex(" ")
        val cardToSearchInURL =replaceWith.replace(URLEncoder.encode( cardToSearch,"UTF-8"),"+")



        var WebData = GetWebSourceCode("https://ygocdb.com/more?search=$cardToSearchInURL&start=$page")



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
            if(monsterMatch == null){
                outPutResult+=it.groupValues[3]+"\n"
            }
            else{
                outPutResult +=monsterMatch.groupValues[1]+"\n身板："+ monsterMatch.groupValues[2]+"\n"
            }

            outPutResult += "{forwardmessage的分割符}"

            resultNumber++
        }
        return outPutResult
    }


    //处理指令
    suspend fun ProcessingCommand(arg: String,user:Contact,GroupID:Long):String{

        val userID = user.id

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

            return "玩家：$playerToCheck\nD.P：$DP\n排名：$Rank\n胜率：$Ratio%\n今日首胜: $FW/1"
        }
        //查历史
        if(arg.startsWith("查历史 ")||arg.startsWith("查记录 ")){
            val matchResult = Regex(""" ([\s\S]*)""").find(arg)?:return "null"
            val playerToCheck = matchResult.groupValues[1]
            val playerNameInURL = URLEncoder.encode( playerToCheck,"UTF-8")

            //获取网页数据
            var WebData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/history?username=${playerNameInURL}&type=1&page_num=30")

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
                if(playerToCheck==it.groupValues[1]){
                    outputResults+= "${it.groupValues[1]} vs ${it.groupValues[2]}\n战况:"
                    if(it.groupValues[11]==playerToCheck){
                        outputResults+="胜利"+" D.P:"
                        if(it.groupValues[12]=="true"){
                            outputResults+="+"+format.format((it.groupValues[3].toFloat()-it.groupValues[5].toFloat()-4)).toString()+" +4(首胜)"
                        }
                        else{
                            outputResults+="+"+format.format((it.groupValues[3].toFloat()-it.groupValues[5].toFloat())).toString()
                        }
                    }
                    else{
                        outputResults+="败北 "+" D.P:"
                        outputResults+="-"+format.format(it.groupValues[5].toFloat()-it.groupValues[3].toFloat()).toString()
                    }

                    outputResults+="\n开始时间:"+it.groupValues[7]+" "+it.groupValues[8] +
                            " 耗时:"+CalculateTime(it.groupValues[7]+" "+it.groupValues[8],it.groupValues[9]+" "+it.groupValues[10])+"{forwardmessage的分割符}"
                }
                else{
                    outputResults+= "${it.groupValues[2]} vs ${it.groupValues[1]}\n战况:"
                    if(it.groupValues[11]==playerToCheck){
                        outputResults+="胜利"+" D.P:"
                        if(it.groupValues[12]=="true"){
                            outputResults+="+"+format.format((it.groupValues[4].toFloat()-it.groupValues[6].toFloat()-4)).toString()+" +4(首胜)"
                        }
                        else{
                            outputResults+="+"+format.format((it.groupValues[4].toFloat()-it.groupValues[6].toFloat())).toString()
                        }
                    }
                    else{
                        outputResults+="败北 "+" D.P:"
                        outputResults+="-"+format.format((it.groupValues[6].toFloat()-it.groupValues[4].toFloat())).toString()
                    }

                    outputResults+="\n开始时间:"+it.groupValues[7]+" "+it.groupValues[8] +
                            " 耗时:"+CalculateTime(it.groupValues[7]+" "+it.groupValues[8],it.groupValues[9]+" "+it.groupValues[10])+"{forwardmessage的分割符}"
                }

                resultNumber++
            }

            outputResults += playerToCheck+ "的MC对战历史"

            return outputResults
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
                var subData =  GroupScribtion.data.getOrPut(GroupID.toLong()){ GroupData(mutableListOf<String>())}

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

            if(page.toInt()<1){
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

                returnMsg +=WordsMatch( duelList[i].Player1) +
                        "  排名："+duelList[i].player1Rank+"  胜率："+duelList[i].player1Ratio + "%"+ "\nVS\n"

                if(duelList[i].StartTime!="-1"){
                    returnMsg +=WordsMatch(duelList[i].Player2) + "  排名："+duelList[i].player2Rank+"  胜率："+duelList[i].player2Ratio+"%"+
                            "\n对局已过去时间："+CalculateTime(duelList[i].StartTime,formatted)+"\n"
                }
                else{
                    returnMsg += WordsMatch(duelList[i].Player2) + "  排名："+duelList[i].player2Rank+"  胜率："+duelList[i].player2Ratio+"%"+
                            "\n对局已过去时间：未知\n"
                }
                if(token != ""){
                    returnMsg += "\n对战房间密码:"+token+duelList[i].id  +"{forwardmessage的分割符}"
                }
                else{
                    returnMsg += "{forwardmessage的分割符}"
                }

            }
            returnMsg += "MC对战列表"
            return returnMsg
        }

        if(arg == "查萌卡排名"){
            var webData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/users?o=pt")
            val resultMatch = Regex(""""username":"(.*?)",[\s\S]*?"pt":(.*?),[\s\S]*?"athletic_win":(\d*),[\s\S]*?"athletic_lose":(\d*),[\s\S]*?"athletic_draw":(\d*),""").
                    findAll(webData).toList()

            if(resultMatch.isNotEmpty()){
                var returnMsg = ""
                var rank = 1

                resultMatch.forEach{
                    returnMsg +=rank.toString() +". 玩家名：" + WordsMatch(it.groupValues[1]) + "\n胜率：" +
                            "%.2f".format((it.groupValues[3].toFloat()/(it.groupValues[3].toFloat()+it.groupValues[4].toFloat()+it.groupValues[5].toFloat()))*100) +
                            "% D.P："+it.groupValues[2].toFloat().toInt().toString() + "{forwardmessage的分割符}"
                    rank ++
                }

                returnMsg += "萌卡天梯排名"
                return returnMsg
            }
        }

        //连接MC服务器相关
        if(arg == "unicorn!"){
            val index = Config.admin.indexOfFirst { it ==  userID}
            if (index == -1) return "你不是管理员，关于如何添加管理员。可以去GitHub上看readme"

            duelList.clear()
            LinkStart()
            delay(5000)
            if(duelList.isEmpty()){
                client.close()
                client.cancel()
                client = HttpClient(OkHttp) {
                    install(WebSockets) {
                        pingInterval = 10_000
                    }
                }
                return "呜呜呜，没连上"
            }
            else{
                return "NT-D!Start!"
            }
        }

        if(arg == "关闭连接"){
            val index = Config.admin.indexOfFirst { it ==  userID}
            if (index == -1) return "你不是管理员，关于如何添加管理员。可以去GitHub上看readme"
            duelList.clear()
            client.close()
            client.cancel()
            client = HttpClient(OkHttp) {
                install(WebSockets) {
                    pingInterval = 10_000
                }
            }
            return "关了,离开了，不要爱了"
        }

        if(arg == "连接状态"){
            if(client.isActive == true){
                return "连着"
            }
            else{
                return "断了"
            }
        }


        //查卡(ourocg)
        //开始查询
        if(arg.startsWith("查卡")||arg.startsWith("卡查")){
            //匹配查卡内容
            val matchResult = Regex("""[卡查|查卡] \s*(.*)""").find(arg)?:return "null"
            val cardToSearch = matchResult.groupValues[1]
            //更新用户数据
            var userSearchData = UpdateUserList(userID)
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
                if(Page>1){
                    Page -= 1
                    IsInUserList.UserSearchPage -=1
                    UserSearchDataList.add(IsInUserList)

                    return SearchCard(IsInUserList.UserSearchContent,(Page-1)*10,0,"")
                }
                else{
                    UserSearchDataList.add(IsInUserList)
                    return "已经是第一页了"
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
                var Page = IsInUserList.UserSearchPage
                val content = SearchCard(IsInUserList.UserSearchContent,Page*10,0,"")

                //是否有内容，没有则是最后一页
                if(content == "没有找到相关的东西")
                {
                    UserSearchDataList.add(IsInUserList)

                    return "已经是最后一页了"
                }
                else{
                    IsInUserList.UserSearchPage+=1
                    UserSearchDataList.add(IsInUserList)

                    return content
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

        if(arg == "md收录"){
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
                return SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"md卡包")
            }
        }

        if(arg == "ocg收录"){
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
                return SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"ocg收录")
            }
        }

        if(arg == "日文调整"){
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
                return SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"日文调整")
            }
        }

        if(arg == "日文faq"){
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
                return SearchCard(IsInUserList.UserSearchContent,(IsInUserList.UserSearchPage-1)*10,IsInUserList.UserSearchCard,"日文faq")
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
            var groupID = GroupID


            if(groupID.toInt() == -1){
                return "添加失败，请在群里使用这个指令"
            }

            val playerName = matchResult.groupValues[1]

            var subData =  GroupScribtion.data.getOrPut(groupID.toLong()){ GroupData(mutableListOf<String>())}

            return subData.AddSubscribeUser(playerName)

        }

        if(arg.startsWith("删除群订阅 ")){

            val matchResult = Regex("""删除群订阅 (.*)""").find(arg)?:return "删除失败，格式为\n删除群订阅 用户名"
            var groupID = GroupID


            if(groupID.toInt() == -1){
                return "添加失败，请在群里使用这个指令"
            }
            val playerName = matchResult.groupValues[1]

            var subData =  GroupScribtion.data.getOrPut(groupID.toLong()){ GroupData(mutableListOf<String>())}

            return subData.DissubscribeUser(playerName)

        }

        if(arg == "显示群订阅"){

            val groupID = GroupID
            if(groupID.toInt() == -1){
                return "请在群里使用这个指令"
            }

            var subData =  GroupScribtion.data.getOrPut(groupID.toLong()){ GroupData(mutableListOf<String>())}

            var returnMsg = "该群订阅玩家:"

            subData.SubscribedUser.forEach{
                returnMsg +="\n" + it
            }
            return returnMsg
        }

        if(arg.startsWith("添加个人订阅 ")){


            val matchResult = Regex("""添加个人订阅 (.*)""").find(arg)?:return "添加失败，格式为\n添加个人订阅 用户名"


            val playerName = matchResult.groupValues[1]

            var subData =  PersonalSubscription.data.getOrPut(userID){ PersonalData(mutableListOf<String>())}

            return subData.AddSubscribeUser(playerName)

        }

        if(arg.startsWith("删除个人订阅 ")){

            val matchResult = Regex("""删除个人订阅 (.*)""").find(arg)?:return "删除失败，格式为\n删除个人订阅 用户名"

            val playerName = matchResult.groupValues[1]

            var subData =  PersonalSubscription.data.getOrPut(userID){ PersonalData(mutableListOf<String>())}

            return subData.DissubscribeUser(playerName)

        }

        if(arg == "显示个人订阅"){


            var subData =  PersonalSubscription.data.getOrPut(userID){ PersonalData(mutableListOf<String>())}

            var returnMsg = ""

            subData.SubscribedUser.forEach{
                val playerNameInURL = URLEncoder.encode( it,"UTF-8")
                var WebData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/user?username=${playerNameInURL}")

                val  PlayerinfoMatch1 = Regex(""","pt":(\d*),".*?"athletic_wl_ratio":"(.*?)","arena_rank":(.*?)}""").find(WebData)

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

            if(accountInfo.isSuccess){
                val accountIDMatch = Regex("""id":(\d*),"username":"(.*?)"""").find(accountInfo.toString())?:return "登录失败 请重试"
                val token=generateToken(accountIDMatch.groupValues[1].toInt())

                var subData =  PersonalSubscription.data.getOrPut(userID){ PersonalData(mutableListOf<String>())}

                subData.PlayerToken = token
                subData.PlayerName = accountIDMatch.groupValues[2]

                return "账号名：" + accountIDMatch.groupValues[2] + "登录成功。\n你的观战和房间token为："+token+"\n如果忘了可以发 萌卡账号token 来随时查看"+
                         "萌卡的ygo服务器为：tiramisu.mycard.moe\n竞技匹配端口为：8911\n观战房间密码为：你的token+对战房间ID"
            }

            return "登录失败，请检查你的账号密码"
        }

        if(arg == "萌卡账号token"){
            var subData =  PersonalSubscription.data.getOrPut(userID){ PersonalData(mutableListOf<String>())}

            if(subData.PlayerToken!="0"){
                return "你当前绑定账号用户名为:"+subData.PlayerName+"\n你的观战和房间token为:"+subData.PlayerToken +
                            "萌卡的ygo服务器为：tiramisu.mycard.moe\n竞技匹配端口为：8911\n观战房间密码为：你的token+对战房间ID"
            }

            return "你还没登录，先去登录吧"
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
            var WebData = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/user?username=${playerNameInURL1}")

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
            var WebData2 = GetWebSourceCode("https://sapi.moecube.com:444/ygopro/arena/user?username=${playerNameInURL2}")

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