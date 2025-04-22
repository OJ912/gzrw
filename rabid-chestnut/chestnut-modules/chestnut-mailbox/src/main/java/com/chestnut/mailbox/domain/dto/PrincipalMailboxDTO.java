package com.chestnut.mailbox.domain.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 校长信箱DTO
 */
@Getter
@Setter
public class PrincipalMailboxDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    private Integer id;

    /**
     * 事由
     */
    private String subject;

    /**
     * 姓名
     */
    private String name;

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 手机号码
     */
    private String phoneNumber;

    /**
     * 邮件内容
     */
    private String content;

    /**
     * 是否查阅
     */
    private Boolean isReviewed;
} 