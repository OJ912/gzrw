package com.chestnut.mailbox.domain.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 校长信箱分页查询DTO
 */
@Getter
@Setter
public class PrincipalMailboxPageDTO {

    private static final long serialVersionUID = 1L;

    /**
     * 页码
     */
    private Integer pageNum = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 10;

    /**
     * 事由
     */
    private String subject;

    /**
     * 姓名
     */
    private String name;

    /**
     * 是否查阅
     */
    private Boolean isReviewed;
    
    /**
     * 查询方式
     * 0 - 默认查询（按id降序）
     * 1 - 查询未查阅（isReviewed为false）
     * 2 - 查询已查阅（isReviewed为true）
     */
    private Integer queryType;
} 