package com.groceryflow.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app")
// @ConfigurationProperties: đọc config từ application.yml vào Java object
// prefix = "app" → map tất cả key bắt đầu bằng "app." vào class này
// Ví dụ: app.public-paths → publicPaths (Spring tự convert kebab-case → camelCase)
public class RouteConfig {

    private List<String> publicPaths;

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        // unmodifiableList: tránh caller vô tình mutate list config
        // Collections.unmodifiableList ném UnsupportedOperationException nếu ai gọi .add()/.remove()
        // Wrap 1 lần trong setter — getPublicPaths() gọi mỗi request, không tạo wrapper mỗi lần
        this.publicPaths = Collections.unmodifiableList(publicPaths);
    }
}
