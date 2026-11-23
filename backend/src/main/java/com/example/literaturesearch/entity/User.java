package com.example.literaturesearch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

<<<<<<< HEAD
    /** 用户自备 DashScope API Key（明文存储，按用户要求不做掩码/加密）；为空时回退到服务器默认 Key */
    @Column(name = "dashscope_api_key", length = 512)
    private String dashscopeApiKey;

    /** 协调者模型覆盖项，为空时使用服务器默认 ORCHESTRATOR_MODEL */
    @Column(name = "chat_model", length = 64)
    private String chatModel;

    /** 子智能体模型覆盖项，为空时使用服务器默认 SUBAGENT_MODEL */
    @Column(name = "subagent_model", length = 64)
    private String subagentModel;

=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
