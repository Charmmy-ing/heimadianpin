package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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
           session.setAttribute("code_"+phone,code);
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
        String cashCode = (String) session.getAttribute("code");
        String code = loginForm.getCode();
        if(!code.equals(cashCode)){
            //3.不一致，返回错误信息
            return Result.fail("验证码错误");
        }


        //4.一致，根据手机号查询用户是否存在


        //5.如果不存在，创建新用户并保存到数据库

        //6.如果存在，返回成功信息

        return null;
    }
}
