package com.hmdp.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Auther: 某某
 * @Date: 2022/9/10 16:47
 * @Description:
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class RedisData {
    /**
     *逻辑过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 存入redis的具体数据
     */
    private Object data;

}
