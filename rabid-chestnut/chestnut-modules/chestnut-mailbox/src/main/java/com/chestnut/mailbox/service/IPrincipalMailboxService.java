package com.chestnut.mailbox.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chestnut.mailbox.domain.PrincipalMailbox;
import com.chestnut.mailbox.domain.dto.PrincipalMailboxDTO;
import com.chestnut.mailbox.domain.dto.PrincipalMailboxPageDTO;
import com.chestnut.mailbox.domain.vo.PrincipalMailboxVO;

import java.util.List;

/**
 * 校长信箱Service接口
 */
public interface IPrincipalMailboxService extends IService<PrincipalMailbox> {

    /**
     * 添加校长信箱
     *
     * @param dto 校长信箱DTO
     * @return 添加结果
     */
    boolean addMailbox(PrincipalMailboxDTO dto);

    /**
     * 更新校长信箱查阅状态
     *
     * @param id 校长信箱ID
     * @param isReviewed 查阅状态
     * @return 更新结果
     */
    boolean updateReviewStatus(Integer id, Boolean isReviewed);

    /**
     * 删除校长信箱
     *
     * @param ids 校长信箱ID列表
     * @return 删除结果
     */
    boolean deleteMailbox(List<Integer> ids);

    /**
     * 分页查询校长信箱列表
     *
     * @param dto 查询条件
     * @return 分页结果
     */
    Page<PrincipalMailboxVO> getMailboxPage(PrincipalMailboxPageDTO dto);

    /**
     * 获取校长信箱详情
     *
     * @param id 校长信箱ID
     * @return 校长信箱详情
     */
    PrincipalMailboxVO getMailboxDetail(Integer id);
} 