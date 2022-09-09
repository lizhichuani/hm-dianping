package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @Auther: 某某
 * @Date: 2022/9/9 12:17
 * @Description:  拦截需要登录的路径
 */
public class LoginInterceptor implements HandlerInterceptor {

       /*@Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
         // 1、获取session
        HttpSession session = request.getSession();
        // 2、获取session中的用户
        Object user = session.getAttribute("user");
        // 3、判断用户是否存在
         if(user == null){
           // 4、不存在，拦截,返回401状态码
             response.setStatus(401);
             return false;
         }
         // 5、存在,用户信息保存到ThreadLocal
          UserHolder.saveUser((UserDTO) user);
         // 6、放行
         return true;
    }*/

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
       //1、判断是否需要拦截（ThreadLocal中是否有用户）
        if(UserHolder.getUser() == null){
            // 没有，需要拦截，设置状态码
            response.setStatus(401);
            // 拦截
            return false;
        }
        // 有用户，则放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
