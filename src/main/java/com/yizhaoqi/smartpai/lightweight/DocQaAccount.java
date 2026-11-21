package com.yizhaoqi.smartpai.lightweight;

import jakarta.persistence.*;

@Entity
@Table(name = "docqa_account")
public class DocQaAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false, unique = true, length = 64)
    String username;
    @Column(nullable = false)
    String passwordHash;
    @Column(nullable = false, length = 16)
    String role;

    protected DocQaAccount() {}
}
