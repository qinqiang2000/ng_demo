package com.invoice.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 数据库查询语法演示测试
 */
@SpringBootTest
public class DatabaseQueryDemoTest {

    @Autowired
    private DatabaseQueryDemo demo;

    @Test
    public void testDatabaseQueryDemo() {
        System.out.println("开始数据库查询语法演示...");
        demo.demonstrateQueries();
        System.out.println("演示完成！");
    }
}