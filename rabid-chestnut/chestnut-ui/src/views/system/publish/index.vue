<template>
  <div class="app-container">
    <el-card>
      <div slot="header">
        <span>一键发布</span>
      </div>

      <!-- 站点选择 -->
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="68px">
        <el-form-item label="站点" prop="siteId">
          <el-select v-model="queryParams.siteId" placeholder="请选择站点" @change="handleSiteChange" style="width: 240px">
            <el-option
              v-for="site in siteOptions"
              :key="site.siteId"
              :label="site.name"
              :value="site.siteId"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleQuery">查询</el-button>
          <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 发布操作区 -->
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button
            type="primary"
            plain
            icon="el-icon-s-promotion"
            size="mini"
            @click="handlePublishSiteIndex"
            :disabled="!queryParams.siteId || publishing"
            :loading="publishingIndex"
          >发布首页</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-dropdown @command="handlePublishAll" :disabled="!queryParams.siteId || publishing">
            <el-button
              type="primary"
              plain
              size="mini"
              icon="el-icon-s-promotion"
              :loading="publishingAll"
            >
              发布全站<i class="el-icon-arrow-down el-icon--right"></i>
            </el-button>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item command="30">已发布内容</el-dropdown-item>
              <el-dropdown-item command="20">待发布内容</el-dropdown-item>
              <el-dropdown-item command="40">已下线内容</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </el-col>
      </el-row>

      <!-- 任务详情对话框 -->
      <el-dialog title="任务详情" :visible.sync="openTaskDetail" width="50%" append-to-body>
        <div v-if="currentTask" class="task-detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="任务ID" :span="2">{{ currentTask.taskId }}</el-descriptions-item>
            <el-descriptions-item label="任务类型">{{ currentTask.taskType }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(currentTask.status)">{{ getStatusText(currentTask.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ parseTime(currentTask.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="进度" v-if="taskProgress && taskProgress.percent !== undefined">
              <el-progress :percentage="taskProgress.percent"></el-progress>
            </el-descriptions-item>
            <el-descriptions-item label="进度信息" :span="2" v-if="taskProgress && taskProgress.progressMessage">
              {{ taskProgress.progressMessage }}
            </el-descriptions-item>
          </el-descriptions>

          <div v-if="taskProgress && taskProgress.errMessages && taskProgress.errMessages.length > 0" class="error-messages">
            <h4>错误信息</h4>
            <div class="error-list">
              <div class="error-item" v-for="(msg, index) in taskProgress.errMessages" :key="index">
                {{ msg }}
              </div>
            </div>
          </div>
        </div>
        <div v-else class="loading-container" style="text-align: center; padding: 40px;">
          <el-empty description="正在加载任务信息..." v-if="loadingTaskDetail" />
          <el-empty description="无法获取任务详情" v-else />
        </div>
      </el-dialog>

      <!-- 发布任务列表 -->
      <div style="margin-bottom: 10px; text-align: right;">
        <el-button
          type="primary"
          icon="el-icon-refresh"
          size="mini"
          @click="getList"
          :loading="loading"
        >刷新列表</el-button>
      </div>
      <el-table v-loading="loading" :data="publishTaskList">
        <el-table-column label="任务ID" align="center" prop="taskId" />
        <el-table-column label="任务类型" align="center" prop="taskType" />
        <el-table-column label="状态" align="center" prop="status">
          <template slot-scope="scope">
            <el-tag :type="getStatusType(scope.row.status)">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="180">
          <template slot-scope="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
          <template slot-scope="scope">
            <el-button
              size="mini"
              type="text"
              icon="el-icon-view"
              @click="handleViewTask(scope.row)"
            >查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total>0"
        :total="total"
        :page.sync="queryParams.pageNum"
        :limit.sync="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>
  </div>
</template>

<script>
import { listSites } from "@/api/contentcore/site";
import { publishSite } from "@/api/contentcore/site";
import { publishToPublishContents } from "@/api/contentcore/content";
import { getTaskInfo, getTaskList } from "@/api/system/async";

export default {
  name: "OneClickPublish",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 总条数
      total: 0,
      // 站点选项
      siteOptions: [],
      // 发布任务列表
      publishTaskList: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        siteId: undefined,
        taskType: "Publish"
      },
      // 状态标识
      publishing: false,      // 任何发布操作进行中
      publishingIndex: false, // 首页发布中
      publishingAll: false,   // 全站发布中
      // 任务重试计数
      retryCount: 0,
      maxRetries: 3,
      // 任务详情
      openTaskDetail: false,
      currentTask: null,
      taskProgress: null,
      loadingTaskDetail: false,
      // 自动刷新计时器
      refreshTimer: null,
      taskDetailTimer: null,
      // 自动刷新间隔（毫秒）
      refreshInterval: 5000
    };
  },
  created() {
    this.getSiteOptions();
    this.getList();
    // 启动自动刷新
    this.startAutoRefresh();
  },
  beforeDestroy() {
    // 组件销毁前清除计时器
    this.stopAutoRefresh();
    this.stopTaskDetailRefresh();
  },
  methods: {
    // 启动自动刷新
    startAutoRefresh() {
      // 先清除可能存在的计时器
      this.stopAutoRefresh();
      // 设置新计时器
      this.refreshTimer = setInterval(() => {
        // 只有当页面显示且有任务ID时才刷新
        if (this.publishTaskList && this.publishTaskList.length > 0) {
          this.getList();
        }
      }, this.refreshInterval);
    },

    // 停止自动刷新
    stopAutoRefresh() {
      if (this.refreshTimer) {
        clearInterval(this.refreshTimer);
        this.refreshTimer = null;
      }
    },

    // 启动任务详情自动刷新
    startTaskDetailRefresh() {
      this.stopTaskDetailRefresh();
      if (this.currentTask && this.currentTask.taskId) {
        this.taskDetailTimer = setInterval(() => {
          this.getTaskDetail(this.currentTask.taskId);
        }, 2000); // 每2秒刷新一次
      }
    },

    // 停止任务详情自动刷新
    stopTaskDetailRefresh() {
      if (this.taskDetailTimer) {
        clearInterval(this.taskDetailTimer);
        this.taskDetailTimer = null;
      }
    },

    /** 获取状态类型 */
    getStatusType(status) {
      if (!status) return 'info';

      // 转为小写进行比较
      status = status.toLowerCase();

      if (status.includes('ready') || status.includes('waiting')) {
        return 'info';
      } else if (status.includes('running')) {
        return 'primary';
      } else if (status.includes('success') || status.includes('completed')) {
        return 'success';
      } else if (status.includes('fail') || status.includes('error')) {
        return 'danger';
      } else if (status.includes('interrupt')) {
        return 'warning';
      }
      return 'info';
    },
    /** 获取状态文本 */
    getStatusText(status) {
      if (!status) return '未知';

      // 转为小写进行比较
      status = status.toLowerCase();

      if (status.includes('ready') || status.includes('waiting')) {
        return '等待中';
      } else if (status.includes('running')) {
        return '运行中';
      } else if (status.includes('success') || status.includes('completed')) {
        return '已完成';
      } else if (status.includes('fail') || status.includes('error')) {
        return '失败';
      } else if (status.includes('interrupt')) {
        return '已中断';
      }
      return status;
    },
    /** 查询站点选项 */
    getSiteOptions() {
      listSites().then(response => {
        this.siteOptions = response.data.rows;
        if (this.siteOptions.length > 0) {
          this.queryParams.siteId = this.siteOptions[0].siteId;
        }
      });
    },
    /** 查询发布任务列表 */
    getList() {
      this.loading = true;
      getTaskList(this.queryParams).then(response => {
        this.publishTaskList = response.data.rows;
        this.total = Number(response.data.total);
        this.loading = false;
      });
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    /** 站点变更 */
    handleSiteChange() {
      this.getList();
    },
    /** 发布站点首页 */
    handlePublishSiteIndex() {
      if (!this.queryParams.siteId) {
        this.$modal.msgWarning("请先选择站点");
        return;
      }

      this.publishingIndex = true;
      this.publishing = true;

      const params = {
        siteId: this.queryParams.siteId,
        publishIndex: true
      };

      publishSite(params).then(response => {
        if (response.code == 200) {
          this.$modal.msgSuccess("首页发布成功，已生成静态文件");
          this.$cache.local.set('publish_flag', "true");
          this.getList();
        } else {
          this.$modal.msgError(response.msg);
        }
      }).catch(error => {
        console.error('发布首页失败:', error);
        this.$modal.msgError("发布失败: " + (error.message || '未知错误'));
      }).finally(() => {
        this.publishingIndex = false;
        this.publishing = false;
      });
    },
    /** 发布全站 */
    handlePublishAll(contentStatus) {
      if (!this.queryParams.siteId) {
        this.$modal.msgWarning("请先选择站点");
        return;
      }

      this.publishingAll = true;
      this.publishing = true;
      this.retryCount = 0;

      // 获取状态描述
      const statusText = contentStatus === '30' ? '已发布' :
                         contentStatus === '20' ? '待发布' :
                         contentStatus === '40' ? '已下线' : '所有';

      // 构建参数，对所有类型使用相同的API调用方式
      const params = {
        siteId: this.queryParams.siteId,
        publishIndex: false,
        contentStatus: contentStatus === 'all' ? undefined : parseInt(contentStatus)
      };

      this.$confirm(`确认要发布该站点下的${statusText}内容吗？此操作可能需要较长时间。`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.doPublishAll(params);
      }).catch(() => {
        this.publishingAll = false;
        this.publishing = false;
        this.$modal.msgWarning('已取消发布操作');
      });
    },

    // 执行全站发布
    doPublishAll(params) {
      publishSite(params).then(response => {
        if (response.code == 200) {
          if (response.data && response.data != "") {
            // 不需要显示进度对话框
            this.$modal.msgSuccess("发布任务已提交，将在后台处理");
            this.$cache.local.set('publish_flag', "true");
            this.getList();
          } else {
            this.$modal.msgSuccess("发布成功，所有静态文件已生成");
            this.$cache.local.set('publish_flag', "true");
            this.getList();
            this.publishingAll = false;
            this.publishing = false;
          }
        } else {
          if (this.retryCount < this.maxRetries) {
            this.retryCount++;
            this.$modal.msgWarning(`发布请求失败，正在重试 (${this.retryCount}/${this.maxRetries})...`);
            setTimeout(() => {
              this.doPublishAll(params);
            }, 2000);
          } else {
            this.$modal.msgError(response.msg || "发布失败，已达到最大重试次数");
            this.publishingAll = false;
            this.publishing = false;
          }
        }
      }).catch(error => {
        console.error('发布全站失败:', error);
        if (this.retryCount < this.maxRetries) {
          this.retryCount++;
          this.$modal.msgWarning(`发布请求出错，正在重试 (${this.retryCount}/${this.maxRetries})...`);
          setTimeout(() => {
            this.doPublishAll(params);
          }, 2000);
        } else {
          this.$modal.msgError("发布失败，已达到最大重试次数");
          this.publishingAll = false;
          this.publishing = false;
        }
      });
    },

    /** 查看任务 */
    handleViewTask(row) {
      if (!row || !row.taskId) {
        this.$modal.msgWarning("无效的任务ID");
        return;
      }

      this.currentTask = row;
      this.openTaskDetail = true;
      this.loadingTaskDetail = true;
      this.taskProgress = null;

      // 获取任务详情
      this.getTaskDetail(row.taskId);

      // 开始自动刷新任务详情
      this.startTaskDetailRefresh();
    },

    /** 获取任务详情 */
    getTaskDetail(taskId) {
      getTaskInfo(taskId).then(response => {
        this.loadingTaskDetail = false;
        if (response.code === 200 && response.data) {
          this.taskProgress = response.data;

          // 如果任务已完成，停止自动刷新
          if (this.taskProgress.status === 'SUCCESS' ||
              this.taskProgress.status === 'FAILED' ||
              this.taskProgress.status === 'INTERRUPTED') {
            this.stopTaskDetailRefresh();
          }
        } else {
          console.error("获取任务详情失败", response);
        }
      }).catch(error => {
        this.loadingTaskDetail = false;
        console.error("获取任务详情出错", error);
      });
    },

    /** 任务完成回调 */
    handleTaskComplete(result) {
      // 已删除，保留空函数
    }
  }
};
</script>

<style scoped>
.task-detail {
  padding: 10px;
}
.error-messages {
  margin-top: 15px;
  border-top: 1px solid #ebeef5;
  padding-top: 15px;
}
.error-messages h4 {
  font-size: 14px;
  margin: 0 0 10px;
  color: #f56c6c;
}
.error-list {
  max-height: 200px;
  overflow-y: auto;
  background-color: #fef0f0;
  border-radius: 4px;
  padding: 10px;
}
.error-item {
  line-height: 1.5;
  font-size: 12px;
  padding: 5px 0;
  border-bottom: 1px dashed #ebeef5;
}
.error-item:last-child {
  border-bottom: none;
}
</style>
