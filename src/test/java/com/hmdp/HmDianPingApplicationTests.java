package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {

      @Autowired
      private ShopServiceImpl shopService;

    /**
     *  模拟后台管理系统向redis中存入热点数据
     */
     @Test
      void testSaveShop(){
            shopService.saveShop2Redis(1L,10L);
      }

}
