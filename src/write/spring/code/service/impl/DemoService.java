package write.spring.code.service.impl;

import write.spring.annotation.LSJService;
import write.spring.code.service.IDemoService;

@LSJService
public class DemoService implements IDemoService {

    public String get(String name) {
        return "我的名字"+name;
    }
}
