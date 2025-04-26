package com.chestnut.mailbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chestnut.common.exception.CommonErrorCode;
import com.chestnut.common.utils.Assert;
import com.chestnut.common.utils.IdUtils;
import com.chestnut.common.utils.StringUtils;
import com.chestnut.mailbox.domain.PrincipalMailbox;
import com.chestnut.mailbox.domain.dto.PrincipalMailboxDTO;
import com.chestnut.mailbox.domain.dto.PrincipalMailboxPageDTO;
import com.chestnut.mailbox.domain.vo.PrincipalMailboxVO;
import com.chestnut.mailbox.mapper.PrincipalMailboxMapper;
import com.chestnut.mailbox.service.IPrincipalMailboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 校长信箱Service实现类
 */
@Service
@RequiredArgsConstructor
public class PrincipalMailboxServiceImpl extends ServiceImpl<PrincipalMailboxMapper, PrincipalMailbox> implements IPrincipalMailboxService {

    private final PrincipalMailboxMapper principalMailboxMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addMailbox(PrincipalMailboxDTO dto) {
        PrincipalMailbox mailbox = new PrincipalMailbox();
        BeanUtils.copyProperties(dto, mailbox);
        mailbox.setCreateTime(LocalDateTime.now());
        mailbox.setIsReviewed(false);
        return this.save(mailbox);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateReviewStatus(Integer id, Boolean isReviewed) {
        Assert.notNull(id, () -> CommonErrorCode.INVALID_REQUEST_ARG.exception("ID不能为空"));
        PrincipalMailbox mailbox = this.getById(id);
        Assert.notNull(mailbox, () -> CommonErrorCode.DATA_NOT_FOUND_BY_ID.exception(id));
        
        mailbox.setIsReviewed(isReviewed);
        return this.updateById(mailbox);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMailbox(List<Integer> ids) {
        Assert.notEmpty(ids, () -> CommonErrorCode.INVALID_REQUEST_ARG.exception("ID列表不能为空"));
        return this.removeByIds(ids);
    }

    @Override
    public Page<PrincipalMailboxVO> getMailboxPage(PrincipalMailboxPageDTO dto) {
        Page<PrincipalMailboxVO> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        return principalMailboxMapper.selectMailboxPage(page, dto);
    }

    @Override
    public PrincipalMailboxVO getMailboxDetail(Integer id) {
        Assert.notNull(id, () -> CommonErrorCode.INVALID_REQUEST_ARG.exception("ID不能为空"));
        PrincipalMailbox mailbox = this.getById(id);
        Assert.notNull(mailbox, () -> CommonErrorCode.DATA_NOT_FOUND_BY_ID.exception(id));
        
        PrincipalMailboxVO vo = new PrincipalMailboxVO();
        BeanUtils.copyProperties(mailbox, vo);
        return vo;
    }
} 