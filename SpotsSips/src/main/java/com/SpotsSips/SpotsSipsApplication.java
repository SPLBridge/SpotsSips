package com.SpotsSips;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.SpotsSips.mapper")
@SpringBootApplication
public class SpotsSipsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpotsSipsApplication.class, args);
    }

}
