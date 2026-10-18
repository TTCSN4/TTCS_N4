package com.ttcs.meetingmanagement.user.creation;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.security.MobileSecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;
import com.nimbusds.jwt.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;

@RestController @RequestMapping("/api/auth")
public class US18LoginController {
    private final UserRepository users;private final BCryptPasswordEncoder encoder;private final MobileSecurityConfig config;
    public US18LoginController(UserRepository users,BCryptPasswordEncoder encoder,MobileSecurityConfig config){this.users=users;this.encoder=encoder;this.config=config;}
    public record Login(@Email @NotBlank String email,@NotBlank String password){}
    public record Token(String accessToken,String tokenType,long expiresIn,String userId){}
    @PostMapping("/login") @Transactional(readOnly=true)
    public Token login(@Valid @RequestBody Login request){
        var u=users.findByEmailIgnoreCase(request.email().trim()).orElseThrow(US18LoginController::unauthorized);
        if(!"ACTIVE".equalsIgnoreCase(u.status)||u.passwordHash==null||!encoder.matches(request.password(),u.passwordHash))throw unauthorized();
        Instant now=Instant.now();
        var claims=new JWTClaimsSet.Builder().subject(u.userId).issuer(config.tokenIssuer()).audience(config.tokenAudience())
            .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600))).jwtID(UUID.randomUUID().toString()).build();
        var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);
        try{jwt.sign(new MACSigner(Base64.getDecoder().decode(config.signingSecret())));}catch(JOSEException e){throw new IllegalStateException("Token signing failed",e);}
        return new Token(jwt.serialize(),"Bearer",3600,u.userId);
    }
    private static ResponseStatusException unauthorized(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials or inactive account");}
}
