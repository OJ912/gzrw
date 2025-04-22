<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb12">
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-check"
          size="mini"
          :disabled="mailboxMultiple"
          v-hasPermi="['mailbox:review']"
          @click="handleReviewed"
          >{{ $t("Mailbox.Reviewed") || "已阅" }}</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-close"
          size="mini"
          :disabled="mailboxMultiple"
          v-hasPermi="['mailbox:review']"
          @click="handleUnreviewed"
          >{{ $t("Mailbox.Unreviewed") || "未阅" }}</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="mailboxMultiple"
          v-hasPermi="['mailbox:delete']"
          @click="handleDelete"
          >{{ $t("Common.Delete") || "删除" }}</el-button
        >
      </el-col>
      <right-toolbar
        :showSearch.sync="showSearch"
        @queryTable="loadMailboxList"
      ></right-toolbar>
    </el-row>
    <el-form
      :model="queryParams"
      ref="queryForm"
      size="small"
      :inline="true"
      style="float: right"
      v-show="showSearch"
    >
      <el-form-item :label="$t('Mailbox.Title') || '标题'" prop="subject">
        <el-input
          v-model="queryParams.subject"
          clearable
          style="width: 160px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item :label="$t('Mailbox.Name') || '姓名'" prop="name">
        <el-input
          v-model="queryParams.name"
          clearable
          style="width: 160px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item
        :label="$t('Mailbox.ReviewStatus') || '审阅状态'"
        prop="isReviewed"
      >
        <el-select
          v-model="queryParams.isReviewed"
          clearable
          style="width: 100px"
        >
          <el-option :label="$t('Mailbox.Reviewed') || '已阅'" :value="true" />
          <el-option
            :label="$t('Mailbox.Unreviewed') || '未阅'"
            :value="false"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button-group>
          <el-button
            type="primary"
            icon="el-icon-search"
            size="small"
            @click="handleQuery"
            >{{ $t("Common.Search") || "搜索" }}</el-button
          >
          <el-button icon="el-icon-refresh" size="small" @click="resetQuery">{{
            $t("Common.Reset") || "重置"
          }}</el-button>
        </el-button-group>
      </el-form-item>
    </el-form>
    <el-table
      v-loading="loading"
      :data="mailboxList"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column
        :label="$t('Mailbox.Title') || '标题'"
        prop="subject"
        :show-overflow-tooltip="true"
      />
      <el-table-column
        :label="$t('Mailbox.Name') || '姓名'"
        prop="name"
        width="100"
        :show-overflow-tooltip="true"
      />
      <el-table-column
        :label="$t('Mailbox.Phone') || '电话'"
        prop="phoneNumber"
        width="120"
        :show-overflow-tooltip="true"
      />
      <el-table-column
        :label="$t('Mailbox.Email') || '邮箱'"
        prop="email"
        width="150"
        :show-overflow-tooltip="true"
      />
      <el-table-column
        :label="$t('Mailbox.Content') || '内容'"
        prop="content"
        :show-overflow-tooltip="true"
      />
      <el-table-column
        :label="$t('Mailbox.ReviewStatus') || '审阅状态'"
        align="center"
        width="100"
      >
        <template slot-scope="scope">
          <el-tag type="success" v-if="scope.row.isReviewed">{{
            $t("Mailbox.Reviewed") || "已阅"
          }}</el-tag>
          <el-tag type="warning" v-else>{{
            $t("Mailbox.Unreviewed") || "未阅"
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="$t('Mailbox.CreateTime') || '创建时间'"
        align="center"
        prop="createTime"
        width="150"
      >
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="$t('Common.Operation') || '操作'"
        align="center"
        class-name="small-padding fixed-width"
        width="220"
      >
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-view"
            @click="handleDetail(scope.row)"
            >{{ $t("Common.Details") || "详情" }}</el-button
          >
          <el-button
            v-if="!scope.row.isReviewed"
            size="mini"
            type="text"
            icon="el-icon-check"
            @click="handleReviewed(scope.row)"
            >{{ $t("Mailbox.Reviewed") || "已阅" }}</el-button
          >
          <el-button
            v-if="scope.row.isReviewed"
            size="mini"
            type="text"
            icon="el-icon-close"
            @click="handleUnreviewed(scope.row)"
            >{{ $t("Mailbox.Unreviewed") || "未阅" }}</el-button
          >
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            >{{ $t("Common.Delete") || "删除" }}</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="loadMailboxList"
    />

    <!-- 详情抽屉 -->
    <el-drawer
      :title="$t('Mailbox.Detail') || '邮箱详情'"
      :visible.sync="detailVisible"
      direction="rtl"
      size="50%"
      :before-close="handleDetailClose"
    >
      <div v-loading="detailLoading" class="drawer-container">
        <el-form :model="detailForm" ref="detailForm" label-width="100px">
          <el-form-item :label="$t('Mailbox.Title') || '标题'">
            <el-input v-model="detailForm.subject" :disabled="!isEdit" />
          </el-form-item>
          <el-form-item :label="$t('Mailbox.Name') || '姓名'">
            <el-input v-model="detailForm.name" :disabled="!isEdit" />
          </el-form-item>
          <el-form-item :label="$t('Mailbox.Phone') || '电话'">
            <el-input v-model="detailForm.phoneNumber" :disabled="!isEdit" />
          </el-form-item>
          <el-form-item :label="$t('Mailbox.Email') || '邮箱'">
            <el-input v-model="detailForm.email" :disabled="!isEdit" />
          </el-form-item>
          <el-form-item :label="$t('Mailbox.Content') || '内容'">
            <el-input
              type="textarea"
              v-model="detailForm.content"
              :rows="6"
              :disabled="!isEdit"
            />
          </el-form-item>
          <el-form-item :label="$t('Mailbox.ReviewStatus') || '审阅状态'">
            <el-switch
              v-model="detailForm.isReviewed"
              :disabled="!isEdit"
              :active-text="$t('Mailbox.Reviewed') || '已阅'"
              :inactive-text="$t('Mailbox.Unreviewed') || '未阅'"
            />
          </el-form-item>
          <el-form-item :label="$t('Mailbox.CreateTime') || '创建时间'">
            <span>{{ parseTime(detailForm.createTime) }}</span>
          </el-form-item>
          <el-form-item v-if="isEdit">
            <el-button type="primary" @click="handleSaveEdit">{{
              $t("Common.Save") || "保存更改"
            }}</el-button>
            <el-button @click="cancelEdit">{{
              $t("Common.Cancel") || "取消"
            }}</el-button>
          </el-form-item>
          <el-form-item v-else>
            <el-button type="primary" @click="startEdit">{{
              $t("Common.Edit") || "编辑"
            }}</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import {
  listMailbox,
  getMailbox,
  reviewMailbox,
  deleteMailbox,
} from "@/api/mailbox/mailbox";

export default {
  name: "MailboxList",
  data() {
    return {
      // 遮罩层
      loading: true,
      detailLoading: false,
      // 选中数组
      selectedMailboxIds: [],
      // 非单个禁用
      mailboxMultiple: true,
      // 显示搜索条件
      showSearch: false,
      // 总条数
      total: 0,
      // 邮箱列表
      mailboxList: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        subject: undefined,
        name: undefined,
        isReviewed: undefined,
      },
      // 详情抽屉
      detailVisible: false,
      detailForm: {},
      // 是否编辑模式
      isEdit: false,
      // 当前编辑的ID
      currentId: null,
    };
  },
  created() {
    this.loadMailboxList();
  },
  methods: {
    // 加载邮箱列表
    loadMailboxList() {
      this.loading = true;
      listMailbox(this.queryParams).then((response) => {
        this.mailboxList = response.data.records || response.data.rows;
        this.total = parseInt(response.data.total);
        this.loading = false;
      });
    },
    // 查询按钮操作
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.loadMailboxList();
    },
    // 重置按钮操作
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.selectedMailboxIds = selection.map((item) => item.id);
      this.mailboxMultiple = !selection.length;
    },
    // 查看详情
    handleDetail(row) {
      this.detailVisible = true;
      this.detailLoading = true;
      this.isEdit = false;
      this.currentId = row.id;

      getMailbox(row.id).then((response) => {
        this.detailForm = response.data;
        this.detailLoading = false;
      });
    },
    // 关闭详情抽屉
    handleDetailClose() {
      this.detailVisible = false;
      this.detailForm = {};
      this.isEdit = false;
      this.currentId = null;
    },
    // 开始编辑
    startEdit() {
      this.isEdit = true;
    },
    // 取消编辑
    cancelEdit() {
      this.isEdit = false;
      // 重新获取数据，恢复原始状态
      this.detailLoading = true;
      getMailbox(this.currentId).then((response) => {
        this.detailForm = response.data;
        this.detailLoading = false;
      });
    },
    // 保存编辑
    handleSaveEdit() {
      // 更新审阅状态
      reviewMailbox(this.currentId, this.detailForm.isReviewed).then(() => {
        this.isEdit = false;
        this.loadMailboxList();
        this.$modal.msgSuccess("更新成功");
      });
    },
    // 标记为已阅
    handleReviewed(row) {
      const ids = row.id ? [row.id] : this.selectedMailboxIds;
      if (ids.length === 0) {
        this.$modal.msgError("请至少选择一条数据");
        return;
      }

      this.$modal.confirm("确认标记为已阅?").then(() => {
        const promises = ids.map((id) => reviewMailbox(id, true));
        Promise.all(promises).then(() => {
          this.loadMailboxList();
          this.$modal.msgSuccess("更新成功");
        });
      });
    },
    // 标记为未阅
    handleUnreviewed(row) {
      const ids = row.id ? [row.id] : this.selectedMailboxIds;
      if (ids.length === 0) {
        this.$modal.msgError("请至少选择一条数据");
        return;
      }

      this.$modal.confirm("确认标记为未阅?").then(() => {
        const promises = ids.map((id) => reviewMailbox(id, false));
        Promise.all(promises).then(() => {
          this.loadMailboxList();
          this.$modal.msgSuccess("更新成功");
        });
      });
    },
    // 删除按钮操作
    handleDelete(row) {
      const ids = row.id ? [row.id] : this.selectedMailboxIds;
      if (ids.length === 0) {
        this.$modal.msgError("请至少选择一条数据");
        return;
      }

      this.$modal.confirm("确认删除选中的数据?").then(() => {
        deleteMailbox(ids).then(() => {
          this.loadMailboxList();
          this.$modal.msgSuccess("删除成功");
        });
      });
    },
  },
};
</script>

<style scoped>
.drawer-container {
  padding: 20px;
}
</style>
