package com.lmeng.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lmeng.dto.LoginFormDTO;
import com.lmeng.dto.ResultUtils;
import com.lmeng.dto.UserDTO;
import com.lmeng.model.User;
import com.lmeng.mapper.UserMapper;
import com.lmeng.service.IUserService;
import com.lmeng.utils.RegexUtils;
import com.lmeng.global.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.lmeng.constant.RedisConstants.*;
import static com.lmeng.constant.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public ResultUtils sendCode(String phone, HttpSession session) {
        //1.检验手机号
        if (!RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误 信息
            return ResultUtils.fail("手机号格式错误");
        }
        //3.符合则生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码到session--->redis  set key value ex 120
        //session.setAttribute("code",code);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码（模拟发送验证码）
        log.debug("发送验证码成功，验证码：{}",code);
        return ResultUtils.ok();
    }

    @Override
    public ResultUtils login(LoginFormDTO loginForm, HttpSession session) {
        //1.验证手机号是否合法
        String phone = loginForm.getPhone();
        if (!RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误 信息
            return ResultUtils.fail("手机号格式错误");
        }
        //2.校验验证码，从redis中获取验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        //缓存中的验证码
        //Object cacheCode = session.getAttribute("code");
        //前端提交过来的验证码，也就是用户输入的
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.toString().equals(code)) {
            //3.若验证码不一致，就返回错误信息
            return ResultUtils.fail("验证码错误");
        }
        //4.若验证码一致就查询用户是否存在 select * from tb_user where phone = ?
        //query就相当于select * from tb_user ，由mybatis-plus提供的
        User user = query().eq("phone", phone).one();
        //5.若用户不存在就创建新用户并保存到数据库
        if(user == null) {
            //创建新用户后用户也要保存到session
            user = createUserWithPhone(phone);
        }

        //6.保存用户到session--->redis
        //6.1随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //6.2将UserDTO对象转为Hash存储（userDTO对象中的属性必须是String类型）
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>()
            , CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //6.3存储登录用户对象的token
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //6.4设置token的有效期 360L = 30分钟 30分钟不操作将登录状态清空，要更新token的有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);

        //7.返回token
        return ResultUtils.ok(token);
    }

    /**
     * 记录用户签到信息
     * @return
     */
    @Override
    public ResultUtils sign() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.获取当天日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key，先将日期按给定格式格式化
        String format = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        String key  = USER_SIGN_KEY + userId + format;
        //4.获取今天是本月的第几天
        int day = now.getDayOfMonth();
        //5.写入redis，注意offset要比第几天小1  SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        return ResultUtils.ok();
    }

    /**
     * 获取签到统计
     * @return
     */
    @Override
    public ResultUtils signCount() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.获取当前日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key，先将日期按给定格式格式化
        String format = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        String key  = USER_SIGN_KEY + userId + format;
        //4.获取今天是本月的第几天
        int day = now.getDayOfMonth();

        //5.获取本月截止本天为止的所有签到记录，，返回的是一个十进制的数字 BITFIELD sign:5:2023/5 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(day)).valueAt(0)
        );
        if(result == null || result.isEmpty()) {
            return ResultUtils.ok(0);
        }
        Long num = result.get(0);
        if(num == null || num == 0) {
            return ResultUtils.ok(0);
        }
        //6.循环遍历
        int count = 0;
        while(true) {
            //7.让这个数字与1做与运算，得到数字的最后一个bit位,判断这个bit是否为0
            if((num & 1) == 0) {
                //若为0 ，说明未签到，结束循环，连续签到天数为0
                break;
            } else {
                //如果不为0，说明已签到，计数器+1
                count++;
            }
            //把数字右移1位，抛弃最后一个bit位，继续下一个bit位
            num = num >> 1;
        }
        return ResultUtils.ok(result);
    }

    /**
     * 接收用户手机号创建新用户
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        //1.创建用户，手机号保存到数据库，昵称随机，头像默认
        User user = new User();
        user.setPhone(phone);
        //用工具类创建一个随机的5位字符组成的昵称
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(5));
        user.setIcon("/imgs/icons/1.webp");
        //2.保存到数据库
        this.save(user);
        return user;
    }
}
