package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
@Resource
private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
           if(RegexUtils.isPhoneInvalid(phone)){
               //2.如果不符合规则，返回错误信息
               return Result.fail("手机号格式错误");
           }
        //3.如果符合规则，生成短信验证码
         String code = RandomUtil.randomNumbers(6);
           //并保存验证码到redis
           stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL , TimeUnit.MINUTES);
        //4.发送验证码到手机号
        log.info("发送验证码到手机号{}，验证码为{}",phone,code);
        //5.返回成功信息
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号格式是否正确
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.如果不符合规则，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //2.从redis中，获取验证码并校验
        String cashCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if(cashCode == null || !code.equals(cashCode.toString())){
            //3.不一致，返回错误信息
            return Result.fail("验证码错误");
        }
        //4.一致，根据手机号查询用户是否存在
        //在数据库中，查询用户是否存在 Select * from user where phone = (phone);
        User user = query().eq("phone",phone).one();
        //5.判断用户是否存在
          //6.不存在
        if(user == null){
            //7.创建新用户并拿到用户对象
            user=createUserWithPhone(phone);
        }
        //8.保存用户到redis里
          //8.1随机为该用户生成一个token，作为登录令牌
        String token = UUID.randomUUID().toString();
          //8.2拿到安全的User对象
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String,Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
          //8.3以hash数据类型保存到redis里
        stringRedisTemplate.opsForHash().putAll("login:token:"+token,userMap);
        //8.4设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL , TimeUnit.MINUTES);
        //9.返回token给前端浏览器
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        //7.保存用户到数据库
        save(user);
        return user;
    }
}
