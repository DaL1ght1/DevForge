package com.example.devforge;

import org.springframework.boot.SpringApplication;

public class TestDevForgeApplication {

    public static void main(String[] args) {
        SpringApplication.from(DevForgeApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
