package com.example.cloans.domain;

import com.example.cloans.domain.enums.Role;
import jakarta.persistence.*;

/** 员工/用户 */
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String login;

    @Column(nullable = false)
    public String name;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    public Post post;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Role role;

    @Column(nullable = false)
    public boolean active = true;

    public User() {}
    public User(String login, String name, Post post, Role role) {
        this.login = login; this.name = name; this.post = post; this.role = role;
    }
}
