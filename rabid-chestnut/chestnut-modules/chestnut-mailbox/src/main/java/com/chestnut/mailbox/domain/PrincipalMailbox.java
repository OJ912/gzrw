package com.chestnut.mailbox.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 校长信箱实体类
 */
@Getter
@Setter
@TableName("gzmdrw_xiaozhangxinxiang")
public class PrincipalMailbox implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID，主键，自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 事由
     */
    @TableField("subject")
    private String subject;

    /**
     * 姓名
     */
    @TableField("name")
    private String name;

    /**
     * 邮箱地址
     */
    @TableField("email")
    private String email;

    /**
     * 手机号码
     */
    @TableField("phone_number")
    private String phoneNumber;

    /**
     * 邮件内容
     */
    @TableField("content")
    private String content;

    /**
     * 创建时间，默认为当前时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 是否查阅，0表示未查阅，1表示已查阅
     */
    @TableField("is_reviewed")
    private Boolean isReviewed;
} 