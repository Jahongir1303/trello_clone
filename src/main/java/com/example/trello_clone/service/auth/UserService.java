package com.example.trello_clone.service.auth;


import com.example.trello_clone.configs.security.UserDetails;
import com.example.trello_clone.domains.auth.AuthUser;
import com.example.trello_clone.dto.auth.LoginRequestDto;
import com.example.trello_clone.dto.auth.UserCreateDto;
import com.example.trello_clone.dto.auth.UserDto;
import com.example.trello_clone.dto.jwt.JwtResponseDto;
import com.example.trello_clone.dto.jwt.RefreshTokenRequest;
import com.example.trello_clone.exceptions.UserNotFoundException;
import com.example.trello_clone.exceptions.ValidationException;
import com.example.trello_clone.mappers.auth.UserMapper;
import com.example.trello_clone.repository.auth.UserRepository;
import com.example.trello_clone.service.token.RefreshTokenService;
import com.example.trello_clone.utils.jwt.JwtUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       @Lazy AuthenticationManager authenticationManager,
                       UserMapper userMapper,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        AuthUser authUser = userRepository.findByEmail(email).
                orElseThrow(() -> new UserNotFoundException("user not found by email %s".formatted(email)));
        return new UserDetails(authUser);
    }


    public JwtResponseDto login(LoginRequestDto request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String accessToken = JwtUtils.accessTokenService.generateToken(userDetails);
        String refreshToken = JwtUtils.refreshTokenService.generateToken(userDetails);
        return new JwtResponseDto(accessToken, refreshToken, "Bearer");
    }

    public UserDto register(UserCreateDto dto) {
        AuthUser authUser = userRepository.save(AuthUser.builder()
                .email(dto.email())
                .password(passwordEncoder.encode(dto.password()))
                .build());
        return userMapper.fromUser(authUser);
    }

    public JwtResponseDto refreshToken(RefreshTokenRequest request) {

        String token = request.token();
        RefreshTokenService refreshTokenService = JwtUtils.refreshTokenService;
        if (!refreshTokenService.isValid(token))
            throw new ValidationException("Refresh token is invalid");
        String username = refreshTokenService.getSubject(token);
        UserDetails userDetails = loadUserByUsername(username);
        String accessToken = JwtUtils.accessTokenService.generateToken(userDetails);

        return new JwtResponseDto(accessToken, request.token(), "Bearer");
    }
}
