package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author lzc
 * @since 2022-9-9
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1、检验手机号(是否不符合手机号规范)
          if(RegexUtils.isPhoneInvalid(phone)){
              //2、如果不符合返回错误信息
              return Result.fail("手机号格式不正确！");
          }
        //3、符合,生成一个验证码
        String code = RandomUtil.randomNumbers(6);
        //4、保存验证码到session
        // session.setAttribute("code",code);

        //4、保存验证码到redis中 // set key value ex 120
         stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY +phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5、发送验证码
        // TODO 调用第三方平台
         log.debug("发送短信验证码成功，验证码："+code);
        // 返回ok
        return Result.ok();
    }

 // session方案
/*
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1、检验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //2、如果不符合返回错误信息
            return Result.fail("手机号格式不正确！");
        }
        //2、校验验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.toString().equals(code)){
            //3、不一致报错
            return Result.fail("验证码错误");
        }
        //4、一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5、判断用户是否存在
          if(user == null){
              //6、不存在，创建新用户并保存
             user = createUserWithPhone(phone);
          }
        //7、保存用户信息到session中
         session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
         return Result.ok();
    }*/


    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1、检验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //2、如果不符合返回错误信息
            return Result.fail("手机号格式不正确！");
        }
        //2、从redis中获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.equals(code)){
            //3、不一致报错
            return Result.fail("验证码错误");
        }
        //4、一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5、判断用户是否存在
        if(user == null){
            //6、不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }
        //7、保存用户信息到redis中
        // 7.1、随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2、将user对象转为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                                    .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString())
                               );
        // 7.3、存储
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        // 7.4、设置token的有效期
         stringRedisTemplate.expire(tokenKey,RedisConstants.LOGIN_USER_TTL,TimeUnit.MINUTES);
        // 8、返回token
         return Result.ok(token);
    }

      private User createUserWithPhone(String phone) {
         // 1、创建用户
         User user = new User();
         user.setPhone(phone);
         user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX +RandomUtil.randomString(10));
         //2、保存用户信息到数据库
         save(user);
         return user;
    }
}
