package write.spring.annotation;


import java.lang.annotation.*;

// 指定作用域范围
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)  // 生命周期
@Documented     // 可被 javadoc工具记录
public @interface LSJRequestParam {
    String value() default "";
}
