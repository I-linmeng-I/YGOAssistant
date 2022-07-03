package Linmeng

import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
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
        version = "0.1.0",
    ) {
        author("linmeng")
    }
) {

    override fun onEnable() {
        logger.info { "我草泥马" }

        Config.reload()
        GroupScribtion.reload()
        PersonalSubscription.reload()

        val command = Command()//消息处理类



        var returnMessage: String = "null"

        globalEventChannel().subscribeAlways<MessageEvent> {
            if(subject === sender){
                returnMessage = command.ProcessingCommand(message.content, sender.id, -1)
            }
            else{
                returnMessage = command.ProcessingCommand(message.content, sender.id, subject.id)
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

                    var returnMsg = buildMessageChain {
                        +message[0]
                        +Image.fromId(updateImage.uploadAsImage(subject).imageId)
                    }

                    subject.sendMessage(returnMsg)
                } else if (returnMessage.indexOf("{forwardmessage的分割符}") > -1) {

                    val nodes = mutableListOf<ForwardMessage.Node>()

                    val message = returnMessage.split("{forwardmessage的分割符}")

                    val preview = mutableListOf<String>("这里是ygo插件查询的返回结果", "如果需要寻找更多请用相关指令")


                    for (i in 0..message.size - 2) {
                        nodes.add(
                            ForwardMessage.Node(
                                bot.id,
                                System.currentTimeMillis().toInt(),
                                bot.nameCardOrNick,
                                buildMessageChain {
                                    +message[i]
                                }
                            )
                        )
                    }

                    val forward = ForwardMessage(
                        preview,
                        message[message.size - 1],
                        message[message.size - 1],
                        subject.id.toString(),
                        message[message.size - 1],
                        nodes
                    )

                    subject.sendMessage(forward)
                } else {
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
        val file1 = File(filePath)
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
}