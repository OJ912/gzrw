package com.chestnut.mailbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chestnut.mailbox.domain.PrincipalMailbox;
import com.chestnut.mailbox.domain.dto.PrincipalMailboxPageDTO;
import com.chestnut.mailbox.domain.vo.PrincipalMailboxVO;
import org.apache.ibatis.annotations.Param;

/**
 * 校长信箱Mapper接口
 */
public interface PrincipalMailboxMapper extends BaseMapper<PrincipalMailbox> {

    /**
     * 分页查询校长信箱列表
     *
     * @param page 分页参数
     * @param dto 查询条件
     * @return 分页结果
     */
    Page<PrincipalMailboxVO> selectMailboxPage(Page<PrincipalMailboxVO> page, @Param("dto") PrincipalMailboxPageDTO dto);
} 