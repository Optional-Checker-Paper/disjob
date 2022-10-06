package cn.ponfee.scheduler.supervisor;

import cn.ponfee.scheduler.common.spring.SpringContextHolder;
import cn.ponfee.scheduler.common.util.GenericUtils;
import cn.ponfee.scheduler.core.base.JobConstants;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Spring boot test base class
 *
 * @param <T> bean type
 * @author Ponfee
 */
/*
@org.junit.runner.RunWith(org.springframework.test.context.junit4.SpringRunner.class)
@SpringBootTest(classes = SchedulerApplication.class)
*/

// 使用的是org.junit.jupiter.api.extension.ExtendWith，
// 所以如果用例要运行在spring容器环境中，就必须使用"@org.junit.jupiter.api.Test"
@ExtendWith(SpringExtension.class)
@SpringBootTest(
    //webEnvironment = SpringBootTest.WebEnvironment.NONE,
    // spring-boot-maven-plugin插件编译打包，故无法引用到类SchedulerApplication.class
    classes = SupervisorTestApplication.class
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
//@ContextConfiguration(classes = { XXX.class })
//@ActiveProfiles({"STG"})
public abstract class SpringBootTestBase<T> {

    private static final Class<?>[] EXCLUDE_CLASSES = {Void.class, Object.class};

    private T bean;
    private final String beanName;

    public SpringBootTestBase() {
        this(null);
    }

    public SpringBootTestBase(String beanName) {
        this.beanName = beanName;
    }

    protected void initiate() {
        // do nothing
    }

    protected void destroy() {
        // do nothing
    }

    protected final T bean() {
        return bean;
    }

    @BeforeAll
    public static void beforeAll() {
        System.setProperty(JobConstants.SPRING_WEB_SERVER_PORT, "8080");
    }

    @BeforeEach
    public final void setUp() {
        Class<T> type = GenericUtils.getActualTypeArgument(getClass(), 0);
        if (!ArrayUtils.contains(EXCLUDE_CLASSES, type)) {
            bean = StringUtils.isBlank(beanName)
                ? SpringContextHolder.getBean(type)
                : SpringContextHolder.getBean(beanName, type);
        }
        initiate();
    }

    @AfterEach
    public final void tearDown() {
        destroy();
    }

    @AfterAll
    public static void afterAll() {
    }

}