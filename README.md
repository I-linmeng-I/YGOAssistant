
# YGOAssistant
一个基于[mirai](https://github.com/mamoe/mirai)QQ机器人框架的游戏王插件

本项目旨在方便游戏王玩家(主要是萌卡的竞技玩家)

如果有bug和想要的新功能还请在issue里发布，因为代码写的跟屎一样。

所以有任何代码相关的建议也可以直接说

或者也可以直接加我QQ群和我说：[631487094](https://jq.qq.com/?_wv=1027&k=5yQoHBE0)



## 更新日志 -v1.4.6 -2024/1/20
增加了查询玩家历史年份月份报告的功能

改了些小bug

修改了些许指令

## 目录
[toc]

## 预计添加
- [ ] 加入高级卡查（通过种族和攻击区间啥的查卡）

## 目前功能

1.  **查游戏王卡片信息**       
    > _卡片数据来自[百鸽](https://ygocdb.com/)_
    
    - 查卡片详细数据
    
    - 查看卡片md或者ocg的收录情况

    - 查看卡片日文的Q&A和调整（中文更新不够及时）
    - 搜索集换社卡片最低价
2.  **查看[萌卡](https://mycard.moe/)玩家信息**

    - 查看特定玩家的排名,胜率等数据
    - 查看特定玩家的对战记录   
        > *只显示最近三十条记录*
    - 查看特定玩家特定年份特定月份对战情况
3.  **查看[萌卡](https://mycard.moe/)当前的对战房间信息**
    - 获得当前在进行对战的竞技匹配房间数据
        > *按玩家排名排序*
        > *个人或者群订阅后置顶被订阅玩家*
    - 在群里订阅特定玩家
        > *在这个群里获得房间列表时会优先显示群订阅玩家*
    - 单独订阅特定玩家
        > *个人订阅的玩家会在他开始对局时私聊推送给你*
    - 绑定萌卡账号到QQ
        > *绑定后推送个人订阅的同时也会推送观战房间密码，就可以直接输入房间密码进入观战*
    - 为特定玩家添加tag
		> *添加t的ag为整个机器人共享，每个玩家可以添加任意数量tag*
		> *添加tag后每次查看对战列表和搜排名的时候，玩家名后面就会出现他的tag<font color="#dd0000">（用来做笔记，记录对手常用卡组）</font><br /> *
4.  **查询天梯前100名的信息**
5.  **查询天梯前25使用率卡组的饼图信息**



6.  **插件支持自己检测更新了！**

![Snipaste_2022-07-04_08-36-28](https://user-images.githubusercontent.com/48974182/177061052-71d01caa-09af-4d34-9f1f-310b9ee09524.png)


现在插件会在初始化时检测是否有更新并提示

7.  **敏感词匹配**

（MC的ID是没有敏感词匹配的，所以很可能导致观战列表发不出来，所以我自己自制了一个手动的敏感词库和匹配）

敏感词库位置为根目录下的`/data/YGOAssistant/WordsMatchLibrary.txt`，没有的话请自己手动创建一个。敏感词库格式：

```
敏感词1
敏感词2
```


## 安装方法
安装方法和一般的mirai插件一样

直接把`.jar`放到安装目录的`plugins/`文件夹下就行了

> 得先运行一次这个插件来生成`配置文件`

> 然后关闭机器人打开安装目录下的`config/`文件夹的`配置文件`来手动设置机器人管理员QQ号<font color="#dd0000">（机器人管理能控制开关萌卡观战房间的连接）</font><br /> 

卡查卡图则是存在安装目录下的`data\YGOAssistant\CardImageCache`文件夹下，可以自己添加或者修改成喜欢的卡图

## 指令/使用教程

| 查卡相关指令                                              | 描述                                    |
|-------------------------------------------------|---------------------------------------|
| `卡查 <关键字>`                            | 开始查询并且返回一个带编号的结果列表<br />如果只有一个结果则直接返回那张卡的数据|
| `进入单卡 <数字>`                                   | 获得卡片列表后，查询列表里那个序号的卡                    |
| `卡片收录 <数字>`                                 | 查询那个序号的卡的md和ocg的收录情况|
| `卡片调整 <数字>`                                 | 查询那个序号的卡的数据库日文调整和faq|
| `卡片价格 <数字>`                                 | 查询那个序号的卡的集换社最低价|
| `上一页`                                 | 卡片列表的上一页，在进入特定单卡后变成上一张|
| `下一页`                                 | 卡片列表的下一页，在进入特定单卡后变成下一张|
| `返回\退出`                              | 从单卡回到查询的关键字的卡片列表|
| `卡片收录`                                  | <font color="#dd0000">进入特定单卡后</font>，查询那张卡的md和ocg的收录情况|
| `卡片调整`                                  | <font color="#dd0000">进入特定单卡后</font>，查询那张卡的数据库日文调整和faq|
| `卡片价格`                                  | <font color="#dd0000">进入特定单卡后</font>，查询那张卡的集换社最低价|
| `释放卡查内存`                              |<font color="#dd0000">**插件管理员指令**</font>清空当前所有人的卡查记录|

| MC玩家相关指令                                   | 描述                                    |
|-------------------------------------------------|---------------------------------------|
| `查分\查成分 <玩家名>`                            | 查询这个玩家的胜率和排名等数据|
| `查历史\查记录 <玩家名>`                          | 查询这个玩家的近30场对战记录 |
| `查萌卡排名`                                   | 查询天梯前100名的玩家 |
| `<x>年<x>月历史 <玩家名>`                                   | 查询x月的玩家的报告，打卡记录为获得了多少次首胜 |

| MC观战相关指令                                   | 描述                                    |
|-------------------------------------------------|---------------------------------------|
| `开始连接`                                       | <font color="#dd0000">**插件管理员指令**</font>开始尝试连接到MC观战服务器|
| `关闭连接`                                       | <font color="#dd0000">**插件管理员指令**</font>断开与MC观战服务器的连接|
| `连接状态`                                       | 查看当前与MC观战服务器的连接情况          |
| `对战列表 <数字>`                                  | 查看当前MC对战列表，数字为页码，每页30个房间|
| `添加群订阅 <玩家名>`                               | 添加当前玩家到群订阅 <font color="#dd0000">*-只能在群里使用*</font>     |
| `删除群订阅 <玩家名>`                               | 删除当前群订阅里的这个玩家 <font color="#dd0000">*-只能在群里使用*</font> |
| `显示群订阅`                                     | 显示当前群里的所有订阅玩家 <font color="#dd0000">*-只能在群里使用*</font> |
| `添加个人订阅 <玩家名>`                               | 添加当前玩家到你的个人订阅                   |
| `添加个人订阅 <玩家名>`                               | 删除你个人订阅里的这个玩家              |
| `显示个人订阅`                                     | 显示你的所有订阅玩家              |
| `登录到萌卡 <账号> <密码>`                            | 绑定你的萌卡账号到QQ               |
| `萌卡账号token`                                  | 显示你绑定的账号的观战token        |
| `添加tag <玩家名> <tag>`                             | 为这位玩家添加tag（只能在群里用）   |
| `删除tag <玩家名> <tag>`                             | 为这位玩家删除tag（只能在群里用）    |
| `查看tag`                                        | 查看当前群聊里的tag                 |

| 其他指令                                         | 描述                                  |
|-------------------------------------------------|---------------------------------------|
| `饼图`                                       | 显示萌卡前25使用率卡组的<font color="#dd0000">**前一天**</font>的饼图                   |


## 使用例


![image](https://i.postimg.cc/52n5S248/Snipaste-2024-01-20-12-19-30.png)

![image](https://i.postimg.cc/KcBnsNcG/Snipaste-2024-01-20-12-21-53.png)
