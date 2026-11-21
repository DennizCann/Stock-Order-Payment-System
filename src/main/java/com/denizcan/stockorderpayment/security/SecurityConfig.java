package com.denizcan.stockorderpayment.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/index.html",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/activate").permitAll()
                        .requestMatchers("/api/v1/auth/me","/api/v1/auth/me/**").authenticated()
                        .requestMatchers("/api/v1/roles/**", "/api/v1/roles").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/channel-stock/**").hasAnyAuthority("ROLE_ADMIN","CHANNELS_VIEW")
                        .requestMatchers("/api/v1/channel-stock/**").hasAnyAuthority("ROLE_ADMIN","CHANNELS_MANAGE")
                        .requestMatchers(HttpMethod.GET,"/api/v1/operations/orders","/api/v1/operations/orders/*/history").hasAnyAuthority("ROLE_ADMIN","OPERATIONS_ORDERS_VIEW")
                        .requestMatchers(HttpMethod.PUT,"/api/v1/operations/orders/*/state").hasAnyAuthority("ROLE_ADMIN","OPERATIONS_ORDERS_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/branches/me/stock").hasAuthority("BRANCH_STOCK_VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/v1/branches/me/sales").hasAuthority("BRANCH_SALE_CREATE")
                        .requestMatchers("/api/v1/branches/me/**").denyAll()
                        .requestMatchers(HttpMethod.GET,"/api/v1/branches").hasAnyAuthority("ROLE_ADMIN","BRANCHES_VIEW")
                        .requestMatchers(HttpMethod.GET,"/api/v1/branches/*/stock").hasAnyAuthority("ROLE_ADMIN","STOCK_VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/v1/branches").hasAnyAuthority("ROLE_ADMIN", "BRANCHES_MANAGE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/branches/*").hasAnyAuthority("ROLE_ADMIN", "BRANCHES_MANAGE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/branches/*/stock/*").hasAnyAuthority("ROLE_ADMIN", "STOCK_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/inventory-operations", "/api/v1/inventory-operation-types")
                            .hasAnyAuthority("ROLE_ADMIN", "STOCK_VIEW", "BRANCH_STOCK_VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/v1/inventory-operations")
                            .hasAnyAuthority("ROLE_ADMIN", "STOCK_MANAGE", "BRANCH_STOCK_MANAGE", "STOCK_TRANSFER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/inventory-operation-types").hasAnyAuthority("ROLE_ADMIN", "STOCK_TYPES_MANAGE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/inventory-operation-types/*").hasAnyAuthority("ROLE_ADMIN", "STOCK_TYPES_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/stock-movements", "/api/v1/stock-movements/locations", "/api/v1/stock-movements/products")
                            .hasAnyAuthority("ROLE_ADMIN", "STOCK_VIEW", "BRANCH_STOCK_VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/v1/stock-movements")
                            .hasAnyAuthority("ROLE_ADMIN", "STOCK_MANAGE", "BRANCH_STOCK_MANAGE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products").hasAnyAuthority("ROLE_ADMIN", "PRODUCTS_CREATE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/*/restore").hasAnyAuthority("ROLE_ADMIN", "PRODUCTS_EDIT")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/*").hasAnyAuthority("ROLE_ADMIN", "PRODUCTS_EDIT")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/*").hasAnyAuthority("ROLE_ADMIN", "PRODUCTS_DELETE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/*/stock", "/api/v1/products/*/stock/**")
                            .hasAnyAuthority("ROLE_ADMIN", "STOCK_MANAGE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/products/*/stock").hasAnyAuthority("ROLE_ADMIN", "STOCK_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/*/stock", "/api/v1/products/*/inventory-transactions")
                            .hasAnyAuthority("ROLE_ADMIN", "STOCK_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**")
                            .hasAnyAuthority("ROLE_ADMIN", "PRODUCTS_VIEW", "STOCK_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders", "/api/v1/orders/**").hasAnyAuthority("ROLE_ADMIN", "ORDERS_VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders").hasAnyAuthority("ROLE_ADMIN", "ORDERS_CREATE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder());
        provider.setUserDetailsService(userDetailsService);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
