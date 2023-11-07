package com.example.project3.controller;

import com.example.project3.Entity.Member;
import com.example.project3.config.jwt.TokenProvider;
import com.example.project3.dto.request.LoginRequest;
import com.example.project3.dto.request.SignupRequest;
import com.example.project3.repository.MemberRepository;
import com.example.project3.service.MemberService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.datafaker.Faker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class MemberControllerTest {

    private final static Faker faker = new Faker(new Locale("ko"));

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void init() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("회원 가입에 성공한다.")
    void successSignUp() throws Exception{
        // given
        // Mock data
        String username = faker.name().lastName() + faker.name().firstName();
        String email = faker.internet().emailAddress();
        String address = faker.address().fullAddress();
        String imageURL = faker.internet().avatar();
        String nickName = faker.name().prefix() + faker.name().firstName();
        String phoneNumber = "010" + faker.numerify("########");
        String gender = faker.options().option("MALE", "FEMALE");

        SignupRequest request = new SignupRequest(username, email, "password12@",
                address, imageURL, nickName, gender, phoneNumber);

        final String requestBody = objectMapper.writeValueAsString(request);

        // when
        ResultActions result = getResult(requestBody); // json 변환

        Member member = memberRepository.findByEmail(request.getEmail()).get();

        // then
        result.andExpect(status().isOk())
                .andExpect(content().string("Signup Successful"));

        // 비밀번호 암호화 테스트
        assertThat(member.getPassword()).isNotEqualTo(request.getPassword());
    }

    @Test
    @DisplayName("회원 가입에 실패한다.(유효하지 않은 이메일과 비밀번호, 전화번호)")
    void failSignup() throws Exception{
        // given
        String username = faker.name().lastName() + faker.name().firstName();
        String email = faker.internet().password();
        String password = faker.internet().password(8, 20);
        System.out.println("password = " + password);
        String address = faker.address().fullAddress();
        String nickName = faker.name().prefix() + faker.name().firstName();
        String phoneNumber = "02" + faker.numerify("########");
        String gender = faker.options().option("MALE", "FEMALE");

        SignupRequest request = new SignupRequest(username, email, password,
                address, null, nickName, gender, phoneNumber);

        final String requestBody = objectMapper.writeValueAsString(request);

        // when
        ResultActions result = getResult(requestBody);


        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").value("Invalid Email"))
                .andExpect(jsonPath("$.password").value("8 ~ 20자, 최소 한개의 특수문자와 숫자, 영문 알파벳을 포함해야 함."))
                .andExpect(jsonPath("$.phoneNumber").value("Invalid phone number"));
    }

    @Test
    @DisplayName("회원 가입에 실패한다.(중복 회원)")
    void duplicateSignup() throws Exception{
        // given
        String username = faker.name().lastName() + faker.name().firstName();
        String email = faker.internet().emailAddress();
        String password = faker.internet().password(8,15) + "12@";
        System.out.println("password = " + password);
        String address = faker.address().fullAddress();
        String nickName = faker.name().prefix() + faker.name().firstName();
        String phoneNumber = "010" + faker.numerify("########");
        String gender = faker.options().option("MALE", "FEMALE");

        SignupRequest request = new SignupRequest(username, email, password,
                address, null, nickName, gender, phoneNumber);

        // when
        memberService.signup(request);

        Member member = memberRepository.findByEmail(request.getEmail()).get();

        // then
        // imageURL에 null값을 받았을 때, default로 설정한 URL이 들어갔는지 확인
        assertThat(member.getImageURL()).isEqualTo("https://meatwiki.nii.ac.jp/confluence/images/icons/profilepics/anonymous.png");

        // when
        final String requestBody = objectMapper.writeValueAsString(request);

        ResultActions result = getResult(requestBody);

        // then
        result.andExpect(status().isConflict())
                .andExpect(content().string("Email already exists"));
    }



    @DisplayName("로그인 성공")
    @Test
    void login() throws Exception {
        // given
        final String url = "/login";

        String username = faker.name().lastName() + faker.name().firstName();
        String email = faker.internet().emailAddress();
        String address = faker.address().fullAddress();
        String imageURL = faker.internet().avatar();
        String nickName = faker.name().prefix() + faker.name().firstName();
        String phoneNumber = "010" + faker.numerify("########");
        String gender = faker.options().option("MALE", "FEMALE");
        String password = "testPassword13@";

        SignupRequest request = new SignupRequest(username, email, password,
                address, imageURL, nickName, gender, phoneNumber);

        final String requestBody = objectMapper.writeValueAsString(request);

        // when
        getResult(requestBody); // 먼저 "/signup"으로 회원가입 신청

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(()->new IllegalArgumentException("Unexpected"));

        LoginRequest loginRequest = new LoginRequest(email, password);

        final String requestBody_2 = objectMapper.writeValueAsString(loginRequest);

        ResultActions resultActions = mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody_2));

        String token = tokenProvider.generateToken(member, Duration.ofHours(1));

        // then
        resultActions.andExpect(status().isCreated())
                .andExpect(content().string(token));
    }


    private ResultActions getResult(String requestBody) throws Exception {
        return mockMvc.perform(post("/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));
    }
}

