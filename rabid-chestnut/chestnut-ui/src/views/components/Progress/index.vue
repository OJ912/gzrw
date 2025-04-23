<template>
    <div class="app-container-progress">
      <el-dialog :title="title"
                 :visible.sync="visible"
                 width="600px"
                 :close-on-click-modal="false"
                 append-to-body
                 center>
      <div class="percent_info">
        <div>{{progressMessage}}</div>
        <div v-if="elapsedTime" class="time-info">
          <span>已用时间: {{elapsedTime}}</span>
          <span v-if="estimatedRemaining" class="estimated-time">预计剩余: {{estimatedRemaining}}</span>
        </div>
      </div>
      
      <el-progress :text-inside="true" :stroke-width="18" :percentage="percent" :status="progressStatus">
        <span v-if="currentCount && totalCount">{{currentCount}}/{{totalCount}}</span>
      </el-progress>
      
      <div class="log-container" v-if="recentLogs && recentLogs.length > 0">
        <div class="log-title">发布日志:</div>
        <div class="logs">
          <div v-for="(log, index) in recentLogs" :key="index" class="log-item">{{log}}</div>
        </div>
      </div>
      
      <div class="retry-container" v-if="progressStatus === 'exception'">
        <el-button type="primary" size="small" @click="retry">重试</el-button>
      </div>
    </el-dialog>
  </div>
</template>
<style scoped>
.percent_info {
  padding: 10px 5px;
  width: 100%;
  line-height: 20px;
  overflow: hidden;
}
.time-info {
  margin-top: 5px;
  font-size: 13px;
  color: #666;
  display: flex;
  justify-content: space-between;
}
.estimated-time {
  margin-left: 20px;
}
.err_messages {
  max-height: 120px;
  overflow-y: auto;
  margin-top: 10px;
  color: #f56c6c;
  font-size: 12px;
  background-color: #fef0f0;
  padding: 8px;
  border-radius: 4px;
}
.log-container {
  margin-top: 15px;
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  padding: 8px;
}
.log-title {
  font-weight: bold;
  margin-bottom: 5px;
  font-size: 14px;
}
.logs {
  max-height: 150px;
  overflow-y: auto;
}
.log-item {
  font-size: 12px;
  line-height: 1.5;
  color: #606266;
  border-bottom: 1px dashed #eee;
  padding: 3px 0;
}
.retry-container {
  margin-top: 15px;
  text-align: center;
}
</style>
<script>
import { getTaskInfo } from "@/api/system/async";
import { getPublishProgress } from "@/api/contentcore/publish";

export default {
  name: "AsyncTaskProgress",
  props: {
    open: {
      type: Boolean,
      default: false,
      required: true
    },
    taskId: {
      type: String,
      required: true,
      default: ""
    },
    title: {
      type: String,
      default: "任务进度",
      required: false
    },
    autoClose: {
      type: Boolean,
      default: true,
      required: false
    },
    interval: {
      type: Number,
      default: 1000,
      required: false
    },
    usePublishProgress: {
      type: Boolean,
      default: false
    }
  },
  watch: {
    open (newVal) {
      this.visible = newVal;
    },
    visible (newVal) {
      if (!newVal) {
        this.handleClose();
      } else {
        this.startInterval();
      }
    },
    taskId(newVal) {
      if (newVal && newVal !== "") {
        if(this.taskId.startsWith('ContentBatchPublish_')) {
          this.usePublishProgress = true;
        }
        this.startInterval();
      }
    }
  },
  computed: {
    formatErrMsg() {
      return this.hasErrMessages() ? this.errMessages.join("<br/>") : "";
    }
  },
  data () {
    return {
      visible: this.open,
      progressMessage: "准备中...",
      errMessages: "",
      percent: 0,
      progressStatus: null,
      timer: undefined,
      resultStatus: undefined,
      currentCount: 0,
      totalCount: 0,
      recentLogs: [],
      elapsedTime: "",
      estimatedRemaining: ""
    };
  },
  created() {
    if(this.taskId && this.taskId.startsWith('ContentBatchPublish_')) {
      this.usePublishProgress = true;
    }
  },
  methods: {
    startInterval () {
      if (this.taskId != "") {
        this.getProgressInfo();
        if (this.timer) {
          clearInterval(this.timer);
        }
        this.timer = setInterval(this.getProgressInfo, this.interval);
      }
    },
    getProgressInfo () {
      if (!this.taskId || this.taskId == '') {
        return;
      }
      
      // 确保taskId是字符串类型
      const taskIdStr = typeof this.taskId === 'object' ? JSON.stringify(this.taskId) : String(this.taskId);
      
      if (this.usePublishProgress || taskIdStr.startsWith('ContentBatchPublish_')) {
        getPublishProgress(taskIdStr).then(response => {
          if (response.code === 200) {
            const data = response.data;
            this.percent = data.percent || 0;
            this.currentCount = data.current || 0;
            this.totalCount = data.total || 0;
            this.progressMessage = data.currentItem || "正在发布内容...";
            this.recentLogs = data.logs || [];
            this.elapsedTime = data.elapsedTime || "";
            this.estimatedRemaining = data.estimatedRemaining || "";
            
            // 根据进度设置状态
            if (this.percent >= 100) {
              clearInterval(this.timer);
              this.progressStatus = "success";
              
              if (this.autoClose) {
                setTimeout(() => {
                  this.visible = false;
                }, 1500);
              }
            } else {
              this.progressStatus = null;
            }
          } else {
            clearInterval(this.timer);
            this.progressStatus = null;
            this.progressMessage = "正在准备中...";
            this.percent = 0;
          }
        }).catch(() => {
          clearInterval(this.timer);
          this.progressStatus = null;
          this.progressMessage = "正在准备中...";
          this.percent = 0;
        });
      } else {
        getTaskInfo(taskIdStr).then(response => {
          if (!response.data) {
            this.progressStatus = null;
            this.progressMessage = "正在准备中...";
            this.percent = 0;
            return;
          }
          
          this.progressMessage = response.data.progressMessage || "正在处理中...";
          this.percent = response.data.percent || 0;
          this.resultStatus = response.data.status;
          
          // 处理任务状态
          if (response.data.status === 'SUCCESS') {
            clearInterval(this.timer);
            this.progressStatus = "success";
            const successMsg = response.data.progressMessage || this.title + this.$t('Component.Progress.Completed');
            this.$modal.msgSuccess(successMsg);
            
            if (this.autoClose) {
              this.visible = false;
            }
          } else if (response.data.status === 'FAILED') {
            clearInterval(this.timer);
            this.progressStatus = "exception";
            this.progressMessage = response.data.progressMessage || "处理失败";
          } else if (response.data.status === 'INTERRUPTED') {
            clearInterval(this.timer);
            this.progressStatus = null;
            this.progressMessage = "任务已中断";
          } else {
            // 其他状态（如处理中）
            this.progressStatus = null;
          }
        }).catch(() => {
          clearInterval(this.timer);
          this.progressStatus = null;
          this.progressMessage = "正在准备中...";
          this.percent = 0;
        });
      }
    },
    hasErrMessages() {
      return this.errMessages && this.errMessages.length > 0;
    },
    retry() {
      this.percent = 0;
      this.progressStatus = null;
      this.progressMessage = "正在重试...";
      this.startInterval();
    },
    handleClose () {
      if (!this.visible) {
        clearInterval(this.timer);
        this.$emit('update:open', false);
        this.$emit("close", { status: this.resultStatus });
        
        // 重置所有状态
        this.percent = 0;
        this.progressMessage = "";
        this.progressStatus = null;
        this.resultStatus = undefined;
        this.currentCount = 0;
        this.totalCount = 0;
        this.recentLogs = [];
        this.elapsedTime = "";
        this.estimatedRemaining = "";
      }
    }
  },
  beforeDestroy() {
    clearInterval(this.timer);
  }
};
</script>