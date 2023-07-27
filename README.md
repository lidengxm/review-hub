# review-hub
# 项目介绍
评客堂，一个博主探店平台，也可以搜索附近店铺信息，一款APP风格，实现了短信验证码登录，可以发表、点赞博文，主页分页展示博文并且按照点赞排行榜排序，关注博主，查看共同用户，推送博主文章，查看店铺详细信息，按照位置搜索店铺等等功能


# 项目总览
* 短信登录
使用Redis共享session来实现

* 商户查询缓存
编写通用缓存访问静态方法，解决了缓存雪崩、缓存穿透等问题，对首页热点博客的缓存预热，提升用户的访问体验，并通过Redisson分布式锁保证集群中同一时刻的定时任务只执行一次。

* 优惠卷秒杀
Redis的计数器功能， 结合Lua完成高性能的Redis操作，同时学会Redis分布式锁的原理，包括Redis的三种消息队列

* 附近的商户
利用Redis的GEOHash来完成对于地理坐标的操作。可以按照距离排序

* UV统计
主要是使用Redis的Hyperloglog数据结构来完成统计功能，占用内存极少

* 用户签到
使用Redis的BitMap数据统计功能，统计用户每个月的统计详情

* 好友关注
基于Set集合的关注、取消关注，共同关注等等功能

* 消息推送
基于Feed流实现关注用户的博客推送功能

* 打人探店
基于List来完成点赞列表的操作，同时基于SortedSet来完成点赞的排行榜功能

# 项目展示

* 短信登录：

![image-20230726174909694](https://alylmengbucket.oss-cn-nanjing.aliyuncs.com/pictures/202307261749728.png)

查看共同关注：


![](https://alylmengbucket.oss-cn-nanjing.aliyuncs.com/pictures/202307241703588.png)

关注/取消关注博主：


![](https://alylmengbucket.oss-cn-nanjing.aliyuncs.com/pictures/202307241730237.png)

发表博客：


![](https://alylmengbucket.oss-cn-nanjing.aliyuncs.com/pictures/202307241931419.png)

商铺按照距离排序：

![image-20230726175039971](https://alylmengbucket.oss-cn-nanjing.aliyuncs.com/pictures/202307261750007.png)

关注博客消息推送：

![image-20230726175135390](https://alylmengbucket.oss-cn-nanjing.aliyuncs.com/pictures/202307261751439.png)
