# review-hub
# 项目介绍
评客堂，一个博主探店平台，也可以搜索附近店铺信息，一款APP风格，实现了短信验证码登录，可以发表、点赞博文，主页分页展示博文并且按照点赞排行榜排序，关注博主，查看共同用户，推送博主文章，查看店铺详细信息，按照位置搜索店铺等等功能


# 项目总览
* 短信登录
使用redis共享session来实现

* 商户查询缓存
通过本章节，我们会理解缓存击穿，缓存穿透，缓存雪崩等问题并进行处理

* 优惠卷秒杀
Redis的计数器功能， 结合Lua完成高性能的redis操作，同时学会Redis分布式锁的原理，包括Redis的三种消息队列

* 附近的商户
利用Redis的GEOHash来完成对于地理坐标的操作

* UV统计
主要是使用Redis来完成统计功能

* 用户签到
使用Redis的BitMap数据统计功能

* 好友关注
基于Set集合的关注、取消关注，共同关注等等功能

* 打人探店
基于List来完成点赞列表的操作，同时基于SortedSet来完成点赞的排行榜功能

# 项目展示

* 短信登录页面
<br>

* 查看共同关注
<br>
![](https://alylmengbucket.oss-cn-nanjing.aliyuncs.com/pictures/202307241703588.png)

* 关注/取消关注博主
<br>
![](https://alylmengbucket.oss-cn-nanjing.aliyuncs.com/pictures/202307241730237.png)

* 发表博客
<br>
![](https://alylmengbucket.oss-cn-nanjing.aliyuncs.com/pictures/202307241931419.png)

