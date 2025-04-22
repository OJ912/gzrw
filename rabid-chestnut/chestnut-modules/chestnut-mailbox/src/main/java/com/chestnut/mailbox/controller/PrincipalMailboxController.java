package com.chestnut.mailbox.controller;

import com.chestnut.common.domain.R;
import com.chestnut.common.log.annotation.Log;
import com.chestnut.common.log.enums.BusinessType;
import com.chestnut.common.security.anno.Priv;
import com.chestnut.common.security.web.BaseRestController;
import com.chestnut.common.utils.StringUtils;
import com.chestnut.mailbox.domain.dto.PrincipalMailboxPageDTO;
import com.chestnut.mailbox.service.IPrincipalMailboxService;
import com.chestnut.system.security.AdminUserType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 校长信箱管理Controller
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mailbox")
public class PrincipalMailboxController extends BaseRestController {

    private final IPrincipalMailboxService principalMailboxService;

    /**
     * 分页查询校长信箱列表
     */
    @Priv(type = AdminUserType.TYPE)
    @GetMapping("/list")
    public R<?> list(PrincipalMailboxPageDTO dto) {
        return R.ok(this.principalMailboxService.getMailboxPage(dto));
    }

    /**
     * 获取校长信箱详情
     */
    @Priv(type = AdminUserType.TYPE)
    @GetMapping("/{id}")
    public R<?> getInfo(@PathVariable Integer id) {
        return R.ok(this.principalMailboxService.getMailboxDetail(id));
    }

    /**
     * 更新校长信箱查阅状态
     */
    @Priv(type = AdminUserType.TYPE)
    @Log(title = "校长信箱管理", businessType = BusinessType.UPDATE)
    @PutMapping("/review/{id}")
    public R<?> updateReviewStatus(@PathVariable Integer id, @RequestParam Boolean isReviewed) {
        return this.principalMailboxService.updateReviewStatus(id, isReviewed) ? R.ok() : R.fail("更新查阅状态失败");
    }

    /**
     * 删除校长信箱
     */
    @Priv(type = AdminUserType.TYPE)
    @Log(title = "校长信箱管理", businessType = BusinessType.DELETE)
    @DeleteMapping
    public R<?> remove(@RequestBody List<Integer> ids) {
        return this.principalMailboxService.deleteMailbox(ids) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 查询未查阅的校长信箱列表
     */
    @Priv(type = AdminUserType.TYPE)
    @GetMapping("/unreviewed")
    public R<?> listUnreviewed(PrincipalMailboxPageDTO dto) {
        dto.setQueryType(1);
        return R.ok(this.principalMailboxService.getMailboxPage(dto));
    }

    /**
     * 查询已查阅的校长信箱列表
     */
    @Priv(type = AdminUserType.TYPE)
    @GetMapping("/reviewed")
    public R<?> listReviewed(PrincipalMailboxPageDTO dto) {
        dto.setQueryType(2);
        return R.ok(this.principalMailboxService.getMailboxPage(dto));
    }
} 