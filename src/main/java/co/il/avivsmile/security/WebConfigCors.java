//package co.il.avivsmile.security;
//
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.web.servlet.config.annotation.CorsRegistry;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
//
//@Configuration
//@EnableWebSecurity
//public class WebConfigCors
//extends WebMvcConfigurationSupport
//{
//
//	@Override
//	protected void addCorsMappings(CorsRegistry registry) {
/// /		registry.addMapping("/**")
/// /        .allowedOrigins("http://localhost:8080")
/// /
/// /        .allowedMethods("POST, GET, OPTIONS, DELETE, PUT, HEAD")
/// /        .allowedHeaders("Content-Type, Authorization")
/// /        .exposedHeaders("*")
/// /        .allowCredentials(true).maxAge(3600);
//		registry.addMapping("/**")
/// /        .allowedOrigins("http://localhost:3000")
/// /
//        .allowedMethods("POST", "GET", "OPTIONS", "DELETE", "PUT", "HEAD");
////        .allowedHeaders("Content-Type, Authorization")
////        .exposedHeaders("Content-Type, Authorization")
////        .allowCredentials(true).maxAge(3600);
//
//
//
//	}
//
//}
