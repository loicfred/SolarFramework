package org.solarframework.authentication.spring;

import org.solarframework.authentication.obj.Account_User;
import org.solarframework.authentication.obj.PersistentLogin;
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

import java.util.Collections;
import java.util.List;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@Configuration
public class SecurityConfig {

    private final OAuth2Service oAuth2UserService;

    public SecurityConfig(OAuth2Service oAuth2UserService) {
        this.oAuth2UserService = oAuth2UserService;
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
            response.sendRedirect("/home");
        };
    }

    @Bean
    public AuthenticationSuccessHandler oauth2LoginSuccessHandler(RememberMeServices rememberMeServices) {
        return (request, response, authentication) -> {
            System.out.println(authentication.getName() + " logged in (OAuth2)!");
            rememberMeServices.loginSuccess(request, response, authentication);
            response.sendRedirect("/home");
        };
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> {
            if (authentication != null) System.out.println(authentication.getName() + " logged out!");
            response.sendRedirect("/accounts/login?logout");
        };
    }

    // Persistent Token Repository (Remember-Me)
    @Bean
    public RememberMeServices rememberMeServices(UserDetailsService userDetailsService) {
        PersistentTokenBasedRememberMeServices services = new PersistentTokenBasedRememberMeServices("my-remember-me-key", userDetailsService, persistentTokenRepository());
        services.setAlwaysRemember(false);
        services.setCookieName("remember-me");
        services.setTokenValiditySeconds(1209600);
        return services;
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository() {
        JdbcTokenRepositoryImpl repo = new JdbcTokenRepositoryImpl();
        if (SolarDBManager.getAvailableSource(PersistentLogin.class).getMissingEntities().contains(PersistentLogin.class)) {
            SolarDBManager.getService(PersistentLogin.class).createSchema(List.of(PersistentLogin.class));
        }
        repo.setDataSource(SolarDBManager.getAvailableSource(PersistentLogin.class).getDataSource());
        return repo;
    }

    // Security Filter Chain
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RememberMeServices rememberMeServices) {
        String[] publicPaths = {
                "/", "/home", "/accounts/**", "/api/v1/**", "/error",
                "/service-worker.js", "/manifest.json", "/offline",
                "/css/**", "/js/**", "/img/**"
        };
        return http
                // Authorization
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths).permitAll()
                        .anyRequest().authenticated()
                )
                // CSRF
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/paypal/**")
                )
                // Form Login
                .formLogin(form -> form
                        .loginPage("/accounts/login")
                        .loginProcessingUrl("/accounts/login")
                        .successHandler(formLoginSuccessHandler())
                        .failureUrl("/accounts/login?error")
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
                        .loginPage("/accounts/login")
                        .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2UserService))
                        .successHandler(oauth2LoginSuccessHandler(rememberMeServices))
                )
                .build();
    }
}