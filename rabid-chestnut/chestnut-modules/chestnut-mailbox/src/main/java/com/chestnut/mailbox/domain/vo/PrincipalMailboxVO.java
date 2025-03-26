package com.chestnut.mailbox.domain.vo;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 校长信箱VO
 */
@Getter
@Setter
public class PrincipalMailboxVO implements Serializable {

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
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否查阅
     */
    private Boolean isReviewed;
} 