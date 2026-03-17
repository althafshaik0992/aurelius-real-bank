package com.aurelius.bank.config;

import com.aurelius.bank.service.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired private UserDetailsServiceImpl userDetailsService;

    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration c) throws Exception {
        return c.getAuthenticationManager();
    }

    @Bean
    public AuthenticationSuccessHandler roleBasedSuccessHandler() {
        return (request, response, authentication) -> {
            String url = "/client/dashboard";
            for (GrantedAuthority ga : authentication.getAuthorities()) {
                String r = ga.getAuthority();
                if (r.equals("ROLE_SUPPORT_AGENT") || r.equals("ROLE_BRANCH_MANAGER") ||
                    r.equals("ROLE_COMPLIANCE_OFFICER") || r.equals("ROLE_SUPER_ADMIN")) {
                    url = "/staff/dashboard"; break;
                }
            }
            response.sendRedirect(url);
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/home", "/login", "/setup-account",
                    "/staff/login", "/css/**", "/js/**", "/images/**",
                    "/error", "/h2-console/**"
                ).permitAll()
                .requestMatchers("/client/**").hasRole("CLIENT")
                .requestMatchers("/staff/**").hasAnyRole("SUPPORT_AGENT","BRANCH_MANAGER","COMPLIANCE_OFFICER","SUPER_ADMIN")
                .requestMatchers("/admin/**").hasRole("SUPER_ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(roleBasedSuccessHandler())
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true).clearAuthentication(true)
                .deleteCookies("JSESSIONID").permitAll()
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        return http.build();
    }
}
