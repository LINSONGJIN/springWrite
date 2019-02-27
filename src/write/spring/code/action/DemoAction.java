package write.spring.code.action;

import write.spring.annotation.LSJAutowired;
import write.spring.annotation.LSJController;
import write.spring.annotation.LSJRequestMapping;
import write.spring.annotation.LSJRequestParam;
import write.spring.code.service.IDemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@LSJController
@LSJRequestMapping("/demo")
public class DemoAction {

    @LSJAutowired
    private IDemoService demoService;

    @LSJRequestMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response, @LSJRequestParam("name") String name){

        String result = demoService.get(name);
        try {
            response.getWriter().write(result);
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
