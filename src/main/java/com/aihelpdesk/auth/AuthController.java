package com.aihelpdesk.auth;

import com.aihelpdesk.user.dto.RegisterEmployeeRequest;
import com.aihelpdesk.user.dto.RegisterEmployeeResponse;
import com.aihelpdesk.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> getCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
            return ResponseEntity.ok(Map.of(
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName()
            ));
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterEmployeeResponse> register(
        @Valid @RequestBody RegisterEmployeeRequest request
    ) {
        RegisterEmployeeResponse response = userService.registerEmployee(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
