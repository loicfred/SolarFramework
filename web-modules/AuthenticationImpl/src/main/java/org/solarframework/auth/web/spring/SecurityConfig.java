package org.solarframework.auth.web.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.auth.obj.*;
import org.solarframework.db.spring.DatabaseManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private final OAuth2Service oAuth2UserService;
    private final DatabaseManager databaseManager;

    public SecurityConfig(OAuth2Service oAuth2UserService, DatabaseManager databaseManager) {
        this.oAuth2UserService = oAuth2UserService;
        this.databaseManager = databaseManager;
    }


    // Password Encoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // UserDetailsService
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            Account_User user = Account_User.getByEmail(username);
            if (user == null) throw new UsernameNotFoundException("User not found");
            return org.springframework.security.core.userdetails.User.builder()
                    .username(user.getEmail())
                    .password(user.getPasswordHash())
                    .disabled(!user.isEmailVerified() && !user.isPhoneVerified())
                    .authorities(Collections.emptyList())
                    .build();
        };
    }

    // Authentication Handlers
    @Bean
    public AuthenticationSuccessHandler formLoginSuccessHandler() {
        return (request, response, authentication) -> {
            System.out.println(authentication.getName() + " logged in (form)!");
            Account_User user = Account_User.getByEmail(authentication.getName());
            if (user != null) {
                user.setLoginCount(user.getLoginCount() + 1);
                user.setLastLoginAt(Instant.now());
                user.setLastSeenAt(Instant.now());
                user.UpdateOnly("LoginCount", "LastLoginAt", "LastSeenAt");
            }
            response.sendRedirect("/home");
        };
    }

    @Bean
    public AuthenticationSuccessHandler oauth2LoginSuccessHandler(RememberMeServices rememberMeServices) {
        return (request, response, authentication) -> {
            System.out.println(authentication.getName() + " logged in (OAuth2)!");
            rememberMeServices.loginSuccess(request, response, authentication);
            Account_User user = Account_User.getByEmail(authentication.getName());
            if (user != null) {
                user.setLoginCount(user.getLoginCount() + 1);
                user.setLastLoginAt(Instant.now());
                user.setLastSeenAt(Instant.now());
                user.UpdateOnly("LoginCount", "LastLoginAt", "LastSeenAt");
            }
            response.sendRedirect("/home");
        };
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> {
            if (authentication != null) System.out.println(authentication.getName() + " logged out!");
            response.sendRedirect("/auth/v1/login?logout");
        };
    }

    // Persistent Token Repository (Remember-Me)
    @Bean
    public RememberMeServices rememberMeServices(UserDetailsService userDetailsService) {
        PersistentTokenBasedRememberMeServices services = new PersistentTokenBasedRememberMeServices("my-remember-me-key", userDetailsService, persistentTokenRepository());
        services.setAlwaysRemember(true);
        services.setCookieName("remember-me");
        services.setTokenValiditySeconds(1209600);
        return services;
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository() {
        JdbcTokenRepositoryImpl repo = new JdbcTokenRepositoryImpl();
        if (databaseManager.getServiceByEntity(PersistentLogins.class).getMissingEntitiesClasses().contains(PersistentLogins.class))
            databaseManager.getServiceByEntity(PersistentLogins.class).createSchemaIfMissing(List.of(PersistentLogins.class));
        repo.setDataSource(databaseManager.getServiceByEntity(PersistentLogins.class).getDataSource());
        return repo;
    }

    // Security Filter Chain
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RememberMeServices rememberMeServices) {
        List<String> publicPaths = new java.util.ArrayList<>(
                List.of("/", "/home", "/error", "/unauthorized", "/auth/**", "/api/**", "/img/**", "/css/**", "/js/**")
        );
        try {
            publicPaths.addAll(databaseManager.getAllWhereDistinct(Web_PathPermission.class, "PermissionID IS NULL").stream().map(Web_PathPermission::getPath).toList());
        } catch (Exception ignored) {}
        log.info("Setting public paths as: {}", publicPaths);
        return http
                // Authorization
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths.toArray(new String[0])).permitAll()
                        .anyRequest().authenticated()
                )
                // CSRF
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/paypal/**")
                )
                // Form Login
                .formLogin(form -> form
                        .loginPage("/auth/v1/login")
                        .loginProcessingUrl("/auth/v1/login")
                        .successHandler(formLoginSuccessHandler())
                        .failureUrl("/auth/v1/login?error")
                        .permitAll()
                )
                // Remember-Me
                .rememberMe(rememberMe -> rememberMe
                        .tokenRepository(persistentTokenRepository())
                        .tokenValiditySeconds(1209600) // 14 days
                        .userDetailsService(userDetailsService())
                )
                // Logout
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(logoutSuccessHandler())
                        .permitAll()
                )
                // OAuth2 Login
                .oauth2Login(oauth -> oauth
                        .loginPage("/auth/v1/login")
                        .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2UserService))
                        .successHandler(oauth2LoginSuccessHandler(rememberMeServices))
                )
                .build();
    }
}