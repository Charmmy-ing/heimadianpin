package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
           if(RegexUtils.isPhoneInvalid(phone)){
               //2.如果不符合规则，返回错误信息
               return Result.fail("手机号格式错误");
           }
        //3.如果符合规则，生成短信验证码并保存验证码到session
         String code = RandomUtil.randomNumbers(6);
           session.setAttribute("code",code);
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
        //2.校验验证码是否正确
        Object cashCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if(cashCode == null || !code.equals(cashCode.toString())){
            //3.不一致，返回错误信息
            return Result.fail("验证码错误");
        }
        //4.一致，根据手机号查询用户是否存在
        //在数据库中，查询用户是否存在 Select * from user where phone = (phone);
        User user = query().eq("phone",phone).one();
        //5.不存在，创建新用户并保存到数据库
        if(user == null){
            //6.创建新用户并拿到
            user=createUserWithPhone(phone);
        }
        //6.保存用户到session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        return Result.ok();
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
