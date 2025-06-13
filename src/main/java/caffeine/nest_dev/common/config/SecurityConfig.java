package caffeine.nest_dev.common.config;

import caffeine.nest_dev.common.exception.CustomAccessDeniedHandler;
import caffeine.nest_dev.common.exception.CustomAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    // 모든 사용자가 접근 가능한 URL 목록 (인증 불필요)
    private static final String[] AUTH_WHITELIST = {
            "/api/auth/signup", "/api/auth/login", "/ws/**",
            "/ws-nest/**", "/oauth2/**"
            // 조회 url 추가
    };

    // 모든 사용자가 접근 가능한 GET METHOD 목록(다른 METHOD에서 URl이 같기 때문에 분리)
    private static final String[] GET_METHOD_AUTH_WHITELIST_PATHS = {
            "/api/profiles/*/careers/**", "/api/mentors/profiles", "/users/*/profiles/*",
            "/api/complaints", "/api/complaints/*", "/api/keywords", "/api/mentors/*/reviews",
            "/api/ticket", "/api/mentor/*/availableConsultations"
    };

    // MENTEE 전용 경로
    private static final String[] POST_METHOD_MENTEE_PATH = {
            "/api/reservations", "/api/reservations/*/reviews"
    };
    private static final String[] GET_METHOD_MENTEE_PATH = {
            "/api/user-coupons", "/api/reservations", "/api/reservations", "/api/reviews"
    };
    private static final String[] PATCH_METHOD_MENTEE_PATH = {
            "/api/reviews/*"
    };
    private static final String[] DELETE_METHOD_MENTEE_PATH = {
            "/api/reviews/*", "/api/reservations/*"
    };

    // MENTOR 전용 경로
    private static final String[] POST_METHOD_MENTOR_PATH = {
            "/api/profiles/*/careers", "/api/mentor/consultations", "/api/profiles"
    };
    private static final String[] GET_METHOD_MENTOR_PATH = {
            "/api/profiles/me"
    };
    private static final String[] PATCH_METHOD_MENTOR_PATH = {
            "/api/profiles/*/careers/*", "/api/careers/**", "/api/mentor/consultations/*",
            "/api/profiles/*"
    };

    private static final String[] DELETE_METHOD_MENTOR_PATH = {
            "/api/profiles/*/careers/*", "/api/careers/**", "/api/mentor/consultations/*",
    };



    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(authorize -> {

                    // 화이트리스트에 있는 경로는 누구나 접근 가능
                    authorize.requestMatchers(AUTH_WHITELIST).permitAll();

                    // 내 문의 조회는 인증 필요!
                    authorize.requestMatchers("/api/complaints/myComplaints").authenticated();

                    // 조회(인증 불필요)
                    authorize.requestMatchers(HttpMethod.GET, GET_METHOD_AUTH_WHITELIST_PATHS)
                            .permitAll();

                    // MENTOR 전용 경로
                    authorize.requestMatchers(HttpMethod.POST, POST_METHOD_MENTOR_PATH)
                            .hasRole("MENTOR")
                            .requestMatchers(HttpMethod.GET, GET_METHOD_MENTOR_PATH)
                            .hasRole("MENTOR")
                            .requestMatchers(HttpMethod.PATCH, PATCH_METHOD_MENTOR_PATH)
                            .hasRole("MENTOR")
                            .requestMatchers(HttpMethod.DELETE, DELETE_METHOD_MENTOR_PATH)
                            .hasRole("MENTOR");

                    // MENTEE 전용 경로
                    authorize.requestMatchers(HttpMethod.POST, POST_METHOD_MENTEE_PATH)
                            .hasRole("MENTEE")
                            .requestMatchers(HttpMethod.GET, GET_METHOD_MENTEE_PATH)
                            .hasRole("MENTEE")
                            .requestMatchers(HttpMethod.PATCH, PATCH_METHOD_MENTEE_PATH)
                            .hasRole("MENTEE")
                            .requestMatchers(HttpMethod.DELETE, DELETE_METHOD_MENTEE_PATH)
                            .hasRole("MENTEE");

                    // 관리자 전용 경로
                    authorize.requestMatchers("/api/admin/**").hasRole("ADMIN");

                    // 추가 정보 입력 경로(임시 역할만 접근 가능)
                    authorize.requestMatchers("/api/users/me/extraInfo").hasRole("GUEST");

                    authorize.anyRequest().authenticated();
                })

                .formLogin(form -> form.disable())

                .httpBasic(httpBasic -> httpBasic.disable())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                )

                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return httpSecurity.build();
    }
}
