package com.syntagi.common.security;

import com.syntagi.auth.entity.User;
import com.syntagi.auth.repository.UserRepository;
import com.syntagi.staff.entity.BusinessUser;
import com.syntagi.staff.repository.BusinessUserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final BusinessUserRepository businessUserRepository;

    public DatabaseUserDetailsService(
            UserRepository userRepository,
            BusinessUserRepository businessUserRepository) {
        this.userRepository = userRepository;
        this.businessUserRepository = businessUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        BusinessUser membership = businessUserRepository
                .findActiveByUserId(user.getId(), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("Active business access not found"));

        return new SyntagiPrincipal(
                user.getId(),
                membership.getBusiness().getId(),
                user.getEmail(),
                user.getPasswordHash(),
                membership.getRole(),
                user.getStatus(),
                membership.getBusiness().getStatus());
    }
}
