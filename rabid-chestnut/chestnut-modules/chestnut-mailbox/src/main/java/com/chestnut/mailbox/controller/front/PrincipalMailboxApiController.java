package com.chestnut.mailbox.controller.front;

import com.chestnut.common.domain.R;
import com.chestnut.common.security.web.BaseRestController;
import com.chestnut.common.utils.StringUtils;
import com.chestnut.mailbox.domain.dto.PrincipalMailboxDTO;
import com.chestnut.mailbox.service.IPrincipalMailboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 校长信箱前端API控制器
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/mailbox")
public class PrincipalMailboxApiController extends BaseRestController {

    private final IPrincipalMailboxService principalMailboxService;

    /**
     * 提交校长信箱
     */
    @PostMapping("/submit")
    public R<?> submit(@RequestBody PrincipalMailboxDTO dto) {
        return this.principalMailboxService.addMailbox(dto) ? R.ok() : R.fail("提交失败");
    }
} 