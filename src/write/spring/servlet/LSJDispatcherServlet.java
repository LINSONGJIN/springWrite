package write.spring.servlet;


import write.spring.annotation.LSJAutowired;
import write.spring.annotation.LSJController;
import write.spring.annotation.LSJRequestMapping;
import write.spring.annotation.LSJService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class LSJDispatcherServlet extends HttpServlet {

    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<String>();

    // IOC 容器,spring里面用的是 currentHashMap
    private Map<String,Object> ioc = new HashMap<String,Object>();

    // url Map
    private Map<String,Method> handlerMapping = new HashMap<String,Method>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 运行阶段执行逻辑
        try {
            doDispatch(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 : " +Arrays.toString(e.getStackTrace()));
        }

    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1 加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        // 2 初始化IOC容器并扫描所有相关的类
        doScann(contextConfig.getProperty("scanPackage"));

        // 3 初始化所有相关联实例并保存到IOC容器中
        doInstance();

        // 4 完成DILsjMVC
        doAutowired();

        // 5 初始胡HandlerMapping
        initHandlerMapping();

        System.out.println("完成初始化");
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if(this.handlerMapping.isEmpty()){ return; }
        String url = req.getRequestURI();

        // 处理成相对路径
        String contextPath = req.getContextPath();
        url = url.replace(contextPath,"").replaceAll("/+","/");

        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 NOT Found");
            return;
        }

        Method m = this.handlerMapping.get(url);
        System.out.println(m);

        // 用反射直接调用

        // 要调用方法，必须要知道方法名，但是现在我们不知道，所以继续投机取巧
        // 通过方法拿到所属的类，再把类名拿出来
        Object obj = this.ioc.get(toLowerFirstCase(m.getDeclaringClass().getSimpleName()));

        Map<String, String[]> params = req.getParameterMap();

        m.invoke(obj,new Object[]{req,resp,params.get("name")}[0]);
    }

    private void initHandlerMapping() {
        if(ioc.isEmpty()) { return; }
        for (Map.Entry<String, Object> entry: ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();

            if(!clazz.isAnnotationPresent(LSJController.class)){ continue; }

            //把修饰在类名上的 url 保存下来，因为方法的url都要跟这个来拼接
            String baseUrl = "";
            if( clazz.isAnnotationPresent(LSJRequestMapping.class)){
                LSJRequestMapping mapping = clazz.getAnnotation(LSJRequestMapping.class);
                baseUrl = mapping.value();
            }

            // 扫描类下所有的公共方法
            Method[] methods = clazz.getMethods();
            for (Method method : methods){
                if(!method.isAnnotationPresent(LSJRequestMapping.class)){ continue; }

                LSJRequestMapping methodMapp = method.getAnnotation(LSJRequestMapping.class);

                String methodUrl = ("/" + baseUrl + "/" + methodMapp.value())
                        .replaceAll("/+","/");

                handlerMapping.put(methodUrl,method);

                System.out.println("Mapped: " + methodUrl + "," + method);
            }
        }
    }

    // 完成ID
    private void doAutowired() {
        if(ioc.isEmpty()) { return; }

        for (Map.Entry<String, Object> entry: ioc.entrySet()){
            Field[] fields = entry.getValue().getClass().getDeclaredFields();

            for(Field field : fields){
                if( !field.isAnnotationPresent(LSJAutowired.class)){ continue; }

                LSJAutowired autowired = field.getAnnotation(LSJAutowired.class);

                String beanName = autowired.value();

                if("".equals(beanName)){
                   beanName = field.getType().getName();
                }

                // 如果遇到的修饰符是 private 或者 protected
                field.setAccessible(true);      // 只要你加了注解，都强制访问

                try {
                    // 为field 赋值
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void doInstance() {
        if(classNames.isEmpty()) { return;}
        try {
            for (String className : classNames){
                Class clazz = Class.forName(className);

                // 要把接口和没添加注解的类都过滤掉
                if(clazz.isAnnotationPresent(LSJController.class)){
                    // 拿到类对象，我们就可以动态地初始化了
                    Object instance = clazz.newInstance();
                    // key默认是类名的首字母小写
                    String beanName = toLowerFirstCase(clazz.getSimpleName());

                } else if(clazz.isAnnotationPresent(LSJService.class)){
                    // 之所以不把判断条件写在一起，是因为接口不同，接口要实例化实现类
                    // 而且注解自定义名称的话，要优先使用
                    LSJService service = (LSJService) clazz.getAnnotation(LSJService.class);
                    String beanName = service.value();
                    if("".equals(service.value())){
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);

                    // 投机取巧，spring底层不是这么干的
                    // 接口的类型作为key，实现类作为value，这样可以避免注入service的时候注入失败
                    for (Class i : clazz.getInterfaces()){

                        // 两个实现类的话
                        if(ioc.containsKey(i.getName())){
                            throw new Exception("the beanName" + i.getName() + "is existed!");
                        }
                        ioc.put(i.getName(),instance);
                    }
                } else{
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // 方法是私有的，只能我自己调，意味着不会故意传null值
    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private void doScann(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.","/"));
        File classDir = new File(url.getFile());

        for (File file : classDir.listFiles()){
            if(file.isDirectory()){
                // 递归
                doScann(scanPackage + "." + file.getName());
            }else{

                if(!file.getName().endsWith(".class")){ continue; }

                String calssName = scanPackage + "." +
                        file.getName().replaceAll(".class","");

                classNames.add(calssName);
            }

        }
    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if( null != is){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


    }
}

