package Linmeng

import Linmeng.Data.PluginData.GroupPlayerTags
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.plugin.version
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.ExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.info
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL


@OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
object YGOAssisttant : KotlinPlugin(
    JvmPluginDescription(
        id = "com.linmeng.YGOAssisttant",
        name = "YGOAssisttant",
        version = "1.2.0",
    ) {
        author("linmeng")
    }
) {

    val command = Command()

    override fun onEnable() {
        logger.info { "YGO助手插件开始加载" }


        Config.reload()
        GroupScribtion.reload()
        PersonalSubscription.reload()
        GroupPlayerTags.reload()

        if(Config.help == ""){
            Config.help = "https://github.com/I-linmeng-I/YGOAssistant/blob/master/README.md"
        }

        logger.info{"配置与数据加载完毕"}

        logger.info{"当前版本${version.toString()}"}
        //获取更新信息
        val webData = GetWebSourceCode("https://api.github.com/repos/I-linmeng-I/YGOAssistant/releases")


        val lastVersion = Regex("""tag_name":"(.*?)",""").find(webData)

        if(lastVersion!=null){
            if(version.toString() != lastVersion.groupValues[1]){
                logger.error("有版本更新！最新版本为${lastVersion.groupValues[1]}")
            }
        }

        //消息处理类



        var returnMessage: String = "null"

        globalEventChannel().subscribeAlways<MessageEvent> {
            if(subject === sender){
                returnMessage = command.ProcessingCommand(message.content, sender, -1)
            }
            else{
                returnMessage = command.ProcessingCommand(message.content, sender, subject.id)
            }

            //返回消息不为空则返回消息
            if (returnMessage != "null") {
                if (returnMessage.indexOf("{加入图片}：url：") > -1) {

                    val message = returnMessage.split("{加入图片}：url：")

                    val updateImage = FileInputStream(
                        httpRequest(
                            "https://cdn.233.momobako.com/ygopro/pics/${message[1]}.jpg!half",
                            message[1]
                        )
                    )

                    //val imageId: String = updateImage.uploadAsImage(subject).imageId

                    if (returnMessage.indexOf("{分割多段}") > -1) {
                        val message1 = message[0].split("{分割多段}")
                        message1.forEach{
                            subject.sendMessage(it)
                        }
                        subject.sendImage(updateImage)
                    }
                    else{
                        subject.sendMessage(message[0])
                        subject.sendImage(updateImage)
                    }


                }
                else if (returnMessage.indexOf("{forwardmessage的分割符}") > -1) {

                    val nodes = mutableListOf<ForwardMessage.Node>()

                    val message = returnMessage.split("{forwardmessage的分割符}")

                    val preview = mutableListOf<String>("这里是ygo插件查询的返回结果", "如果需要寻找更多请用相关指令")


                    message.forEach {
                        nodes.add(
                            ForwardMessage.Node(
                                bot.id,
                                time = -System.currentTimeMillis().toInt(),
                                bot.nameCardOrNick,
                                buildMessageChain {
                                    +it
                                }
                            )
                        )
                    }

                    nodes.removeAt(nodes.size-1)

                    val forward = RawForwardMessage(nodes).render(object : ForwardMessage.DisplayStrategy {
                        override fun generateTitle(forward: RawForwardMessage): String {
                            return "查询到的${message[message.size-1]}"
                        }

                        override fun generateSummary(forward: RawForwardMessage): String {
                            return "查看${nodes.size}条结果"
                        }
                    })

                    try {
                        subject.sendMessage(forward)
                    }
                    catch (e: RuntimeException) {
                        logger.info { "报错了，$e" }
                    }

                }
                else {
                    subject.sendMessage(returnMessage)
                }

                //subject.sendMessage("${sender.id}发了${message.content}")
            }
        }


    }


    @Throws(Exception::class)
    private fun httpRequest(uri: String,cardName:String): String? {
        val path = "./data/YGOAssistant/CardImageCache/"
        val filePath = "$cardName.jpg"

        val file1 = File(path+filePath)
        if(file1.exists()){
            return path+filePath
        }

        val startTime = System.currentTimeMillis()
        val url = URL(uri)
        val conn: HttpURLConnection = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("referer", "") //这是破解防盗链添加的参数
        conn.addRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.67"
        )
        conn.setRequestMethod("GET")
        conn.setConnectTimeout(5 * 1000)
        val inStream: InputStream = conn.getInputStream() //通过输入流获取图片数据
        readInputStream(inStream, filePath, path)
        return path+filePath
    }
    @Throws(java.lang.Exception::class)
    private fun readInputStream(inStream: InputStream, filePath: String, path: String) {
        val file = File(path)
        val file1 = File(path+filePath)
        if (!file.exists()) file.mkdirs()
        if (!file1.exists()) {
            val fos = FileOutputStream(File(path+filePath))
            val buffer = ByteArray(102400)
            var len = 0
            while (inStream.read(buffer).also { len = it } != -1) {
                fos.write(buffer, 0, len)
            }
            inStream.close()
            fos.flush()
            fos.close()
        } else {
            inStream.close()
        }

    }

    fun GetWebSourceCode(url:String):String{

        val doc = URL(url).readText()
        return doc
    }
}