package com.example.cloans.domain;

import jakarta.persistence.*;

/** 岗位：持有密级许可（clearance） */
@Entity
@Table(name = "posts")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String name;

    /** 可接触的最高密级（1 内部 / 2 秘密 / 3 机密） */
    @Column(nullable = false)
    public int clearance;

    public Post() {}
    public Post(String name, int clearance) { this.name = name; this.clearance = clearance; }
}
