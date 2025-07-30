package com.invoice.core;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.ListType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CEL语法测试类 - Java对象方案
 * 
 * 演示如何使用Java对象作为CEL表达式的上下文数据
 * 优点：直接使用现有业务对象，开发简单，类型安全
 * 缺点：需要反射访问，性能相对较低
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CEL语法测试 - Java对象方案")
public class CelExpressionTest {

    private CelCompiler compiler;

    @BeforeEach
    void setUp() throws CelValidationException, CelEvaluationException {
        // 1. 创建CEL编译器 - 使用Map类型来简化类型处理
        compiler = CelCompilerFactory.standardCelCompilerBuilder()
                // 使用Map类型来表示用户对象，避免复杂的Java类型注册
                .addVar("user", MapType.create(SimpleType.STRING, SimpleType.DYN))
                .addVar("threshold", SimpleType.DOUBLE)
                .build();
    }

    @Test
    @DisplayName("基本Java对象CEL表达式测试")
    void testBasicJavaObjectExpression() throws CelValidationException, CelEvaluationException {
        try {
            // 2. CEL表达式编译 - 使用正确的CEL数值语法
            String expression = "user.age > 25 && user.salary > threshold && 'Java' in user.skills";
            CelAbstractSyntaxTree ast = compiler.compile(expression).getAst();

            // 3. 创建运行时环境
            CelRuntime runtime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
            CelRuntime.Program program = runtime.createProgram(ast);

            // 4. 准备执行上下文 - 将Java对象转换为Map
            User userObj = new User("张三", 28, "技术部", 15000.0,
                    Arrays.asList("Java", "Spring", "MySQL"));

            // 将Java对象转换为Map，这样CEL可以直接访问
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("name", userObj.getName());
            userMap.put("age", (long) userObj.getAge()); // 转换为long类型
            userMap.put("department", userObj.getDepartment());
            userMap.put("salary", userObj.getSalary());
            userMap.put("skills", userObj.getSkills());

            Map<String, Object> context = new HashMap<>();
            context.put("user", userMap);
            context.put("threshold", 12000.0);

            // 5. 执行表达式
            Object result = program.eval(context);
            
            // 使用格式化字符串和文本块优化输出
            String output = """
                    ========================
                    Java对象信息:
                    姓名: %s
                    年龄: %d
                    部门: %s
                    薪资: %.2f
                    技能: %s
                    表达式: %s
                    表达式执行结果: %s
                    """.formatted(
                    userObj.getName(),
                    userObj.getAge(),
                    userObj.getDepartment(),
                    userObj.getSalary(),
                    userObj.getSkills(),
                    expression,
                    result
            );
            System.out.println(output);
            
            assertTrue((Boolean) result, "表达式应该返回true");

        } catch (CelEvaluationException e) {
            e.printStackTrace();
            fail("CEL表达式执行失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("复杂业务规则表达式测试")
    void testComplexBusinessRuleExpression() throws CelValidationException, CelEvaluationException {
        // 更复杂的业务规则表达式
        String complexExpression = """
                user.department == '技术部' &&
                user.age >= 25 &&
                user.salary > 10000.0 &&
                size(user.skills) >= 2 &&
                ('Java' in user.skills || 'Python' in user.skills)
                """;

        CelAbstractSyntaxTree ast = compiler.compile(complexExpression).getAst();
        CelRuntime runtime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
        CelRuntime.Program program = runtime.createProgram(ast);

        // 测试多个用户
        List<User> users = Arrays.asList(
                new User("李四", 30, "技术部", 18000.0, Arrays.asList("Java", "Spring")),
                new User("王五", 22, "市场部", 8000.0, Arrays.asList("Excel", "PPT")),
                new User("赵六", 27, "技术部", 14000.0, Arrays.asList("Python", "Django")));

        for (User userObj : users) {
            // 将Java对象转换为Map
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("name", userObj.getName());
            userMap.put("age", (long) userObj.getAge()); // 转换为long类型
            userMap.put("department", userObj.getDepartment());
            userMap.put("salary", userObj.getSalary());
            userMap.put("skills", userObj.getSkills());

            Map<String, Object> context = Map.of("user", userMap);
            boolean result = (Boolean) program.eval(context);
            
            // 使用格式化字符串优化输出
            System.out.printf("========================%n%s 是否符合条件: %s%n", 
                    userObj.getName(), result);
            
            // 验证预期结果
            if ("李四".equals(userObj.getName()) || "赵六".equals(userObj.getName())) {
                assertTrue(result, userObj.getName() + " 应该符合条件");
            } else {
                assertFalse(result, userObj.getName() + " 不应该符合条件");
            }
        }
    }

    /**
     * 业务领域对象 - 用户类
     */
    public static class User {
        private String name;
        private int age;
        private String department;
        private double salary;
        private List<String> skills;

        // 构造函数
        public User(String name, int age, String department, double salary, List<String> skills) {
            this.name = name;
            this.age = age;
            this.department = department;
            this.salary = salary;
            this.skills = skills;
        }

        // Getter方法 - CEL需要这些来访问字段
        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public String getDepartment() {
            return department;
        }

        public double getSalary() {
            return salary;
        }

        public List<String> getSkills() {
            return skills;
        }
    }
}