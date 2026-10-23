package com.hotel.system.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    /**
     * Standard Jackson module that serializes all 64-bit Long and long values as JSON strings.
     * This protects 64-bit Snowflake IDs from being truncated by JavaScript's
     * IEEE-754 double precision floating-point limit (MAX_SAFE_INTEGER = 2^53 - 1 = 9007199254740991).
     */
    @Bean
    public Module longToStringModule() {
        SimpleModule module = new SimpleModule("LongToStringModule");
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        return module;
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(longToStringModule());
        mapper.findAndRegisterModules();
        return mapper;
    }
}
