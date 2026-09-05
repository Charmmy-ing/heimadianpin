package com.heima.redisdemo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest(classes = RedisDemoApplication.class)
class RedisDemoApplicationTests {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testString() throws JsonProcessingException {
        User user = new User("canlu", 18);
        String json = mapper.writeValueAsString(user);
        stringRedisTemplate.opsForValue().set("user:200", json);
        String jsonUser = stringRedisTemplate.opsForValue().get("user:200");
        User user1 = mapper.readValue(jsonUser, User.class);
        System.out.println(user1);
    }

}