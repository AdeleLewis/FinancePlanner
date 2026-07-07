package com.budget.auth;

import com.budget.auth.AuthService.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/status")
    public StatusView status(Authentication authentication) {
        final boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        return new StatusView(!authService.isSetup(), authenticated);
    }

    /** First-run only: create the owner password, then sign the caller in. */
    @PostMapping("/setup")
    public StatusView setup(@RequestBody PasswordRequest body,
                            HttpServletRequest request, HttpServletResponse response) {
        if (authService.isSetup()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A password is already set");
        }
        try {
            authService.setup(body.password());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        establishSession(request, response);
        return new StatusView(false, true);
    }

    @PostMapping("/login")
    public StatusView login(@RequestBody PasswordRequest body,
                            HttpServletRequest request, HttpServletResponse response) {
        final LoginResult result = authService.verify(body.password());
        if (result == LoginResult.LOCKED) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts — try again in " + authService.lockSecondsRemaining() + "s");
        }
        if (result == LoginResult.WRONG_PASSWORD) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect password");
        }
        establishSession(request, response);
        return new StatusView(false, true);
    }

    @PostMapping("/logout")
    public StatusView logout(HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        authService.lock();   // seal the vault: bank secrets are unreadable until the next login
        return new StatusView(!authService.isSetup(), false);
    }

    private void establishSession(final HttpServletRequest request, final HttpServletResponse response) {
        request.getSession(true);
        request.changeSessionId();   // session fixation defence: never keep the pre-login session id
        final Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "owner", null, AuthorityUtils.createAuthorityList("ROLE_OWNER"));
        final SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    public record StatusView(boolean setupRequired, boolean authenticated) {
    }

    public record PasswordRequest(String password) {
    }
}
