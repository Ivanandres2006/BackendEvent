package com.example.dbeventservice.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    @Bean
    public Module hibernateModule() {
        Hibernate6Module m = new Hibernate6Module();
        m.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING); // don't auto-load lazies
        m.enable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);
        return m;
    }
}
