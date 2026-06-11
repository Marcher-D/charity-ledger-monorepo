package com.tcl.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.JwtException;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Filter that runs on incoming HTTP requests and authenticates Bearer JWT tokens.
// If the token is valid, it stores the logged-in user in Spring Security's context.
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Creates/parses/validates JWT tokens.
    private final JwtService jwtService;

    // Loads Account data from the database in Spring Security's UserDetails format.
    private final AccountDetailsService accountDetailsService;

    // Constructor injection: Spring provides these two security beans.
    public JwtAuthenticationFilter(JwtService jwtService, AccountDetailsService accountDetailsService) {
        this.jwtService = jwtService;
        this.accountDetailsService = accountDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // The frontend sends JWTs in this header:
        // Authorization: Bearer <token>
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            // No JWT found. Continue to the next filter.
            // Public endpoints may still work; protected endpoints will fail later.
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Remove "Bearer " from the beginning of the header.
            // "Bearer " has 7 characters, so substring(7) starts at the token text.
            String token = header.substring(7);
            // The JWT subject is the login email.
            String email = jwtService.extractSubject(token);
            // getAuthentication() == null means Spring Security has not logged in
            // anyone for this request yet. If another filter already authenticated
            // the request, we do not overwrite it here.
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Load the account from the database as UserDetails.
                UserDetails userDetails = accountDetailsService.loadUserByUsername(email);
                if (jwtService.isValid(token, userDetails)) {
                    // Build Spring Security's authentication object.
                    // UsernamePasswordAuthenticationToken is Spring Security's container for:
                    // principal = who is logged in, credentials = proof/secret, authorities = roles.
                    // credentials is null because JWT already proved identity for this request.
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    // Attach request-specific details like remote address/session id.
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    // Store authenticated user for the rest of this request.
                    // Controllers can later access it with @AuthenticationPrincipal.
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (JwtException | IllegalArgumentException |
                 org.springframework.security.core.userdetails.UsernameNotFoundException exception) {
            // Invalid/expired token, or a token for an account that no longer
            // exists (e.g. browser kept a JWT from before a database reset, or
            // from another deployment sharing the same secret). Treat the
            // request as unauthenticated and let the authorization layer
            // decide: public endpoints (register/login) keep working, protected
            // endpoints get a clean 401 from the entry point. Rejecting here
            // broke registration for every browser holding a stale token.
            SecurityContextHolder.clearContext();
        }

        // Continue to the next filter/controller after authentication work is done.
        filterChain.doFilter(request, response);
    }
}
